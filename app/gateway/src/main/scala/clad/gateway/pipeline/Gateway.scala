package clad.gateway.pipeline

import clad.audit.{KeyManagementService, SignedAuditRecord}
import clad.core.Surface
import clad.gateway.config.{Canonical, GatewayConfig, Template, Templates}
import clad.gateway.epg.{Epg, EpgResult}
import clad.gateway.store.GatewayStore
import clad.integrity.{ComponentId, DegradedAuditRecord, DegradedStatus, FailureAction, FailurePosture, GilEntry, InteractionId, SignedDegradedRecord}
import clad.runtime.AuditRecord
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.Instant
import scala.util.control.NonFatal
import GatewayResult.*

final class Gateway(cfg: GatewayConfig, epg: Epg, store: GatewayStore, components: KeyManagementService,
                    supervisor: KeyManagementService, upstream: Upstream, forwarder: PassthroughUpstream,
                    containment: Path, clock: () => Instant):

  private val ComponentGateway = "gateway"
  private val ComponentEpg = "epg"
  private val Lock = Object()

  private val posture: Map[String, FailurePosture] = Map(
    "Critical" -> FailurePosture.FailClosed, "Standard" -> FailurePosture.FailOpenFlagged, "Low" -> FailurePosture.FailOpen)

  /** What a step hands the next: the checked, pseudonymised request with its template, model and rendered prompt. */
  private final case class Checked(req: GovernedRequest, t: Template, principal: String, model: String, rendered: String)

  def complete(req: GovernedRequest): GatewayResult =
    val started = System.nanoTime()
    val spent = Array(0L)
    val iid = InteractionId.generate().value
    val result =
      try run(req, iid, spent)
      catch case NonFatal(e) =>
        System.err.println(s"CLAD_GATEWAY_INTERNAL_ERROR iid=$iid ${e.getClass.getName}: ${e.getMessage}")
        Unavailable(Some(iid), s"internal error: ${e.getMessage}")
    val (id, outcome) = result match
      case Delivered(i, o, _)  => (i, o)
      case Blocked(i, r)       => (i, s"blocked:$r")
      case Rejected(_)         => (iid, "rejected")
      case Unavailable(i, _)   => (i.getOrElse(iid), "unavailable")
    val overheadMs = (System.nanoTime() - started - spent(0)) / 1000000L
    println(s"CLAD_GATEWAY_CALL iid=$id template=${req.templateId} outcome=$outcome overhead_ms=$overheadMs")
    result

  private def run(req: GovernedRequest, iid: String, spent: Array[Long]): GatewayResult =
    check(req) match
      case Left(why) => Rejected(why)
      case Right(c) =>
        val now = clock()
        contained(c.t.project) match
          case Left(err) =>
            System.err.println(s"CLAD_GATEWAY_CONTAINMENT_UNREADABLE $err")
            blockWithRecord(c, iid, now, "containment_unreadable")
          case Right(Some(_)) => blockWithRecord(c, iid, now, "contained")
          case Right(None) =>
            register(c, iid, now) match
              case Left(_) => Blocked(iid, "gil_unavailable")
              case Right(_) =>
                runEpg(c, iid) match
                  case Left(why)  => Blocked(iid, why)
                  case Right(()) =>
                    body(c).fold(Rejected(_), b => callUpstream(c, b, iid, spent))

  // 1 + 2 + 3: check, pseudonymise, model
  private def check(req: GovernedRequest): Either[String, Checked] =
    for
      t <- cfg.templates.get(req.templateId).toRight(s"unknown template ${req.templateId}")
      _ <- Either.cond(!req.messages.exists(m => m.value.get("role").contains(ujson.Str("system"))), (),
        "client system messages are not allowed")
      rendered <- Templates.render(t, req.vars)
      model <- req.modelTier match
        case None => Right(t.model)
        case Some(tier) => cfg.templates.modelFor(tier).toRight(s"unknown model tier $tier")
    yield Checked(req, t, Principal.pseudonymise(req.principal), model, rendered)

  // 4: containment file, {"<project>": "<reason>"}; absent means nothing contained, unreadable is an error
  private def readContainment(): Either[String, Map[String, String]] =
    if !Files.exists(containment) then Right(Map.empty)
    else
      try
        ujson.read(Files.readString(containment)) match
          case o: ujson.Obj =>
            o.value.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) {
              case (acc, (k, ujson.Str(s))) => acc.map(_ + (k -> s))
              case (_, (k, _))              => Left(s"$containment: reason for $k is not a string")
            }
          case _ => Left(s"$containment: not a JSON object")
      catch
        case e: java.io.IOException => Left(s"cannot read $containment: ${e.getMessage}")
        case NonFatal(e)            => Left(s"malformed $containment: ${e.getMessage}")

  private def contained(project: String): Either[String, Option[String]] =
    Lock.synchronized(readContainment()).map(_.get(project))

  private def blockWithRecord(c: Checked, iid: String, now: Instant, reason: String): GatewayResult =
    register(c, iid, now) match
      case Left(_) => Blocked(iid, "gil_unavailable")
      case Right(_) =>
        val rec = appendRecord(iid, ComponentGateway, Surface.Delivery, s"block:$reason", Vector.empty,
          Canonical.sha256(c.rendered), cfg.governance.configDigest(c.t.project))
        rec.left.foreach(e => System.err.println(s"CLAD_GATEWAY_RECORD_FAILED iid=$iid $e"))
        Blocked(iid, reason)

  private def appendRecord(iid: String, component: String, surface: Surface, outcome: String,
                           entries: Vector[clad.runtime.AuditEntry], artifactDigest: String,
                           configDigest: String): Either[String, SignedAuditRecord] =
    val at = clock()
    store.appendLinked(prev => AuditRecord(artifactDigest, entries, configDigest, at, prev, Some(InteractionId(iid)),
      Some(component), Some(surface), Some(outcome)), components)

  // 5: GIL first
  private def metadata(c: Checked, evidenceDigest: Option[String]): Map[String, String] =
    val o = c.req.options.value
    Map("project" -> c.t.project, "principal" -> c.principal, "tier" -> c.t.tier, "template_id" -> c.t.id,
        "template_version" -> c.t.version, "model" -> c.model) ++
      c.req.session.map("session" -> _) ++
      o.get("temperature").map("temperature" -> _.render()) ++
      o.get("max_tokens").map("max_tokens" -> _.render()) ++
      evidenceDigest.map("evidence_digest" -> _)

  private def evidenceText(e: ujson.Value): String = e match
    case ujson.Str(s) => s
    case other        => ujson.write(other)

  private def register(c: Checked, iid: String, now: Instant): Either[String, Unit] =
    store.register(GilEntry(InteractionId(iid), now, metadata(c, c.req.evidence.map(e => Canonical.sha256(evidenceText(e))))))

  // 6: EPG
  private def runEpg(c: Checked, iid: String): Either[String, Unit] =
    val outcome = for
      r <- epg.evaluate(c.t, c.rendered, c.model, clock())
      _ <- store.putConfig(r.configDigest, c.t.project, r.configJson)
      _ <- store.putArtifact(r.artifactDigest, r.artifactJson)
      _ <- appendRecord(iid, ComponentEpg, Surface.Prompt, if r.blocked then "block" else "pass", r.entries,
        r.artifactDigest, r.configDigest)
    yield r
    outcome match
      case Left(why) => Left(s"epg_unavailable: $why")
      case Right(r) if r.blocked =>
        Left(r.entries.find(!_.satisfied).map(_.constraint.property.value).getOrElse("epg_blocked"))
      case Right(_) => Right(())

  // 7: body
  private def body(c: Checked): Either[String, ujson.Obj] =
    val system = ujson.Obj("role" -> "system", "content" -> c.rendered)
    val withEvidence: Either[String, Vector[ujson.Obj]] = c.req.evidence match
      case None => Right(c.req.messages)
      case Some(e) =>
        val payload = c.t.evidenceFormat match
          case "rows_json"      => Right(ujson.write(e))
          case "document_pages" => Right(evidenceText(e).replace("<data", "&lt;data").replace("</data", "&lt;/data"))
          case other            => Left(s"${c.t.id}: evidence is not accepted (evidence_format $other)")
        payload.flatMap { p =>
          val idx = c.req.messages.lastIndexWhere(_.value.get("role").contains(ujson.Str("user")))
          if idx < 0 then Left("evidence needs a user message")
          else c.req.messages(idx).value.get("content") match
            case Some(ujson.Str(s)) =>
              val m = ujson.Obj.from(c.req.messages(idx).value.toSeq.map((k, v) => if k == "content" then k -> ujson.Str(s + "\n\n<data>\n" + p + "\n</data>") else k -> v))
              Right(c.req.messages.updated(idx, m))
            case _ => Left("evidence needs a user message with text content")
        }
    withEvidence.map { ms =>
      val base = ujson.Obj.from(c.req.options.value.toSeq)
      base("model") = c.model
      base("messages") = ujson.Arr.from(system +: ms)
      base
    }

  // 8: upstream
  private def validReply(v: ujson.Value): Boolean = v match
    case o: ujson.Obj => o.value.get("choices") match
      case Some(a: ujson.Arr) => a.value.headOption.exists(_.isInstanceOf[ujson.Obj])
      case _                  => false
    case _ => false

  private def callUpstream(c: Checked, b: ujson.Obj, iid: String, spent: Array[Long]): GatewayResult =
    val t0 = System.nanoTime()
    val reply =
      try upstream.complete(b, iid)
      catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
    spent(0) += System.nanoTime() - t0
    reply match
      case Right(v) if validReply(v) => Delivered(iid, "pass", v)
      case Right(_)                  => fail(c.t, iid, "not a chat completion")
      case Left(why)                 => fail(c.t, iid, why)

  private def fail(t: Template, iid: String, why: String): GatewayResult =
    val p = posture.getOrElse(t.tier, FailurePosture.FailClosed)
    val reason = s"upstream: $why"
    val rec = DegradedAuditRecord(InteractionId(iid), clock(), ComponentId.Roc, DegradedStatus.Degraded, reason, p,
      FailurePosture.actionFor(p))
    SignedDegradedRecord.sign(rec, supervisor, ComponentId.Supervisor) match
      case Left(e) => System.err.println(s"GOVERNANCE_DEGRADED_UNSIGNED iid=$iid $e")
      case Right(signed) => store.degraded(signed).left.foreach(e => System.err.println(s"CLAD_GATEWAY_DEGRADED_NOT_STORED iid=$iid $e"))
    if rec.actionTaken == FailureAction.Blocked then Blocked(iid, "upstream_unavailable")
    else Unavailable(Some(iid), reason)

  def passthrough(name: String, path: String, templateId: String, principal: String, session: Option[String],
                  body: String): GatewayResult =
    val iid = InteractionId.generate().value
    try
      cfg.templates.get(templateId) match
        case None => Rejected(s"unknown template $templateId")
        case Some(t) =>
          val c = Checked(GovernedRequest(templateId, principal, session, Vector.empty, Map.empty, None, None, ujson.Obj()),
            t, Principal.pseudonymise(principal), t.model, "")
          register(c, iid, clock()) match
            case Left(_) => Blocked(iid, "gil_unavailable")
            case Right(_) =>
              val reply =
                try forwarder.forward(name, path, body, iid)
                catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
              reply match
                case Right((status, text)) => Delivered(iid, "pass", ujson.Obj("status" -> status, "body" -> text))
                case Left(why)             => fail(t, iid, why)
    catch case NonFatal(e) =>
      System.err.println(s"CLAD_GATEWAY_INTERNAL_ERROR iid=$iid ${e.getClass.getName}: ${e.getMessage}")
      Unavailable(Some(iid), s"internal error: ${e.getMessage}")

  def contain(project: String, reason: String): Either[String, Unit] = update(project, Some(reason))
  def release(project: String): Either[String, Unit] = update(project, None)

  private def update(project: String, reason: Option[String]): Either[String, Unit] =
    Lock.synchronized {
      for
        _ <- Either.cond(cfg.governance.projects.contains(project), (), s"unknown project $project")
        current <- readContainment()
        next = reason.fold(current - project)(r => current + (project -> r))
        _ <- write(next)
      yield ()
    }

  private def write(m: Map[String, String]): Either[String, Unit] =
    try
      val dir = Option(containment.toAbsolutePath.getParent).getOrElse(containment.toAbsolutePath)
      val tmp = Files.createTempFile(dir, containment.getFileName.toString, ".tmp")
      try
        Files.writeString(tmp, ujson.write(ujson.Obj.from(m.toSeq.sortBy(_._1).map((k, v) => k -> ujson.Str(v)))))
        Files.move(tmp, containment, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Right(())
      finally Files.deleteIfExists(tmp)
    catch case e: java.io.IOException => Left(s"cannot write $containment: ${e.getMessage}")
