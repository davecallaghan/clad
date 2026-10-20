package clad.gateway.pipeline

import clad.audit.{KeyManagementService, SignedAuditRecord}
import clad.core.Surface
import clad.gateway.config.{Canonical, GatewayConfig, Template, Templates}
import clad.gateway.epg.{Epg, EpgResult}
import clad.gateway.roc.{Fallbacks, Guard, ModelOutput, Roc}
import clad.gateway.store.GatewayStore
import clad.integrity.{ComponentId, DegradedAuditRecord, DegradedStatus, FailureAction, FailurePosture, GilEntry, InteractionId, SignedDegradedRecord}
import clad.runtime.AuditRecord
import java.nio.file.{Files, Path, StandardCopyOption}
import java.time.Instant
import java.util.concurrent.Executor
import scala.util.control.NonFatal
import GatewayResult.*

final class Gateway(cfg: GatewayConfig, epg: Epg, store: GatewayStore, components: KeyManagementService,
                    supervisor: KeyManagementService, upstream: Upstream, forwarder: PassthroughUpstream,
                    containment: Path, clock: () => Instant, guard: Guard, background: Executor):

  private val ComponentGateway = "gateway"
  private val ComponentEpg = "epg"
  private val ComponentRoc = "roc"
  private val SuccessClass = 2
  private val HttpOk = 200
  private val SafeResponse = "fallback:safe_response"
  /** What a non-blocking posture delivers when the ROC could not evaluate the output: the degraded record says so. */
  private val Degraded = "degraded"
  private val RocUnavailable = "roc_unavailable"
  private val Lock = Object()
  /** What a client sees for a server-side failure: the detail goes to stderr with the interaction id, never to the client. */
  private val InternalError = "internal_error"
  private val EpgUnavailable = "epg_unavailable"
  private val ServerErrorClass = 5
  private val TooManyRequests = 429

  private val posture: Map[String, FailurePosture] = Map(
    "Critical" -> FailurePosture.FailClosed, "Standard" -> FailurePosture.FailOpenFlagged, "Low" -> FailurePosture.FailOpen)

  /** A ROC retry is a second upstream call within the same interaction. */
  private val roc = Roc(cfg, guard, (b, iid) =>
    try upstream.complete(b, iid)
    catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}"))

  /** What a step hands the next: the checked, pseudonymised request with its template, model and rendered prompt. */
  private final case class Checked(req: GovernedRequest, t: Template, principal: String, model: String, rendered: String,
                                   messages: Vector[ujson.Obj])

  def complete(req: GovernedRequest): GatewayResult =
    val started = System.nanoTime()
    val spent = Array(0L)
    val iid = InteractionId.generate().value
    val result =
      try run(req, iid, spent)
      catch case NonFatal(e) =>
        System.err.println(s"CLAD_GATEWAY_INTERNAL_ERROR iid=$iid ${e.getClass.getName}: ${e.getMessage}")
        Unavailable(Some(iid), InternalError)
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
        guarded(c, iid) {
          register(c, iid, clock()) match
            case Left(_) => Blocked(iid, "gil_unavailable")
            case Right(_) =>
              signal(c, iid)
              runEpg(c, iid) match
                case Left(why) => Blocked(iid, why)
                case Right(()) => callUpstream(c, body(c), iid, spent)
        }

  /** Containment comes first; an unreadable containment file blocks rather than allows. */
  private def guarded(c: Checked, iid: String)(proceed: => GatewayResult): GatewayResult =
    contained(c.t.project) match
      case Left(err) =>
        System.err.println(s"CLAD_GATEWAY_CONTAINMENT_UNREADABLE $err")
        blockWithRecord(c, iid, clock(), "containment_unreadable")
      case Right(Some(_)) => blockWithRecord(c, iid, clock(), "contained")
      case Right(None)    => proceed

  // 1 + 2 + 3: check, pseudonymise, model
  private val AllowedRoles = Set("user", "assistant", "tool")

  private def pseudonym(p: String): Either[String, String] =
    val out = Principal.pseudonymise(p)
    Either.cond(!out.contains('@'), out, "principal_not_pseudonymisable")

  private def check(req: GovernedRequest): Either[String, Checked] =
    for
      t <- cfg.templates.get(req.templateId).toRight(s"unknown template ${req.templateId}")
      _ <- Either.cond(req.messages.forall(m => m.value.get("role").exists {
        case ujson.Str(r) => AllowedRoles.contains(r)
        case _            => false
      }), (), "client messages may only have the roles user, assistant and tool")
      principal <- pseudonym(req.principal)
      rendered <- Templates.render(t, req.vars)
      model <- req.modelTier match
        case None => Right(t.model)
        case Some(tier) => cfg.templates.modelFor(tier).toRight(s"unknown model tier $tier")
      messages <- withEvidence(t, req)
    yield Checked(req, t, principal, model, rendered, messages)

  private val DataTag = "(?i)<(/?data)".r

  /** The client messages with the evidence appended to the last user message; every failure is a rejection. */
  private def withEvidence(t: Template, req: GovernedRequest): Either[String, Vector[ujson.Obj]] = req.evidence match
    case None => Right(req.messages)
    case Some(e) =>
      val payload = t.evidenceFormat match
        case "rows_json"      => Right(ujson.write(e).replace("<", "\\u003c"))
        case "document_pages" => Right(DataTag.replaceAllIn(evidenceText(e), m => "&lt;" + m.group(1)))
        case other            => Left(s"${t.id}: evidence is not accepted (evidence_format $other)")
      payload.flatMap { p =>
        val idx = req.messages.lastIndexWhere(_.value.get("role").contains(ujson.Str("user")))
        if idx < 0 then Left("evidence needs a user message")
        else req.messages(idx).value.get("content") match
          case Some(ujson.Str(s)) =>
            val m = ujson.Obj.from(req.messages(idx).value.toSeq.map((k, v) =>
              if k == "content" then k -> ujson.Str(s + "\n\n<data>\n" + p + "\n</data>") else k -> v))
            Right(req.messages.updated(idx, m))
          case _ => Left("evidence needs a user message with text content")
      }

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
        val digest = cfg.governance.configDigest(c.t.project, cfg.roc)
        // the record names this config, so the bundle must be able to resolve it
        val rec = store.putConfig(digest, c.t.project, cfg.governance.configJson(c.t.project, cfg.roc)).flatMap(_ =>
          appendRecord(iid, ComponentGateway, Surface.Delivery, s"block:$reason", Vector.empty,
            Canonical.sha256(c.rendered), digest))
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

  // 5b: input signals (monitoring data: a failed write is logged and the call goes on; signals never change the outcome)
  private val inputs = InputDetectors(cfg.inputDetectors)

  private def signal(c: Checked, iid: String): Unit =
    val at = clock()
    inputs.scan(InputDetectors.texts(c.req.messages, c.req.vars)).foreach(s =>
      store.inputSignal(iid, s, at).left.foreach(e =>
        System.err.println(s"CLAD_GATEWAY_SIGNAL_NOT_STORED iid=$iid detector=${s.detector} $e")))

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
      case Left(why) =>
        System.err.println(s"CLAD_GATEWAY_EPG_UNAVAILABLE iid=$iid $why")
        Left(EpgUnavailable)
      case Right(r) if r.blocked =>
        Left(r.entries.find(!_.satisfied).map(_.constraint.property.value).getOrElse("epg_blocked"))
      case Right(_) => Right(())

  // 7: body (infallible: the messages were validated in check)
  private def body(c: Checked): ujson.Obj =
    val base = ujson.Obj.from(c.req.options.value.toSeq)
    base("model") = c.model
    base("messages") = ujson.Arr.from(ujson.Obj("role" -> "system", "content" -> c.rendered) +: c.messages)
    base

  // 8: upstream
  private def validReply(v: ujson.Value): Boolean = v match
    case o: ujson.Obj => o.value.get("choices") match
      case Some(a: ujson.Arr) => a.value.headOption.exists(ch => ch.isInstanceOf[ujson.Obj] && ch.obj.get("message").exists(_.isInstanceOf[ujson.Obj]))
      case _                  => false
    case _ => false

  private def callUpstream(c: Checked, b: ujson.Obj, iid: String, spent: Array[Long]): GatewayResult =
    val t0 = System.nanoTime()
    val reply =
      try upstream.complete(b, iid)
      catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
    spent(0) += System.nanoTime() - t0
    reply match
      case Right(v) if validReply(v) => output(c, b, v, iid)
      case Right(_)                  => fail(c.t, iid, "not a chat completion")
      case Left(why)                 => fail(c.t, iid, why)

  private def fail(t: Template, iid: String, why: String): GatewayResult =
    val reason = s"upstream: $why"
    if degrade(t, iid, reason) == FailureAction.Blocked then Blocked(iid, "upstream_unavailable")
    else Unavailable(Some(iid), reason)

  /** A supervisor-signed degraded record (component roc) with the tier's posture; returns the action it records. */
  private def degrade(t: Template, iid: String, reason: String): FailureAction =
    val p = posture.getOrElse(t.tier, FailurePosture.FailClosed)
    val rec = DegradedAuditRecord(InteractionId(iid), clock(), ComponentId.Roc, DegradedStatus.Degraded, reason, p,
      FailurePosture.actionFor(p))
    SignedDegradedRecord.sign(rec, supervisor, ComponentId.Supervisor) match
      case Left(e) => System.err.println(s"GOVERNANCE_DEGRADED_UNSIGNED iid=$iid $e")
      case Right(signed) => store.degraded(signed).left.foreach(e => System.err.println(s"CLAD_GATEWAY_DEGRADED_NOT_STORED iid=$iid $e"))
    rec.actionTaken

  // 9: ROC (spec 8.3). Critical runs its classifier before delivery; other tiers classify on the background executor.

  /** The ROC could not evaluate or record: Critical blocks; other tiers deliver per their posture (ProceededFlagged or
    * ProceededSilent), which is what the degraded record states, with outcome `degraded`. `unchecked` is what may be
    * delivered: never more than the reply's role, text and tool calls; None when even those could not be read. */
  private def rocFailed(t: Template, iid: String, why: String, unchecked: Option[ujson.Value]): GatewayResult =
    System.err.println(s"CLAD_GATEWAY_ROC_FAILED iid=$iid template=${t.id} $why")
    if degrade(t, iid, s"roc: $why") == FailureAction.Blocked then Blocked(iid, RocUnavailable)
    else unchecked.fold(Unavailable(Some(iid), RocUnavailable))(Delivered(iid, Degraded, _))

  /** The reply cut down to what the ROC would have evaluated, for a fail-open delivery. */
  private def readable(reply: ujson.Value): Option[ujson.Value] =
    ModelOutput.of(reply).flatMap(Fallbacks.deliver(reply, _)).toOption

  private def appendRoc(t: Template, iid: String, outcome: String, entries: Vector[clad.runtime.AuditEntry],
                        artifactDigest: String): Either[String, SignedAuditRecord] =
    val digest = cfg.governance.configDigest(t.project, cfg.roc)
    // the record names this config, so the bundle must be able to resolve it
    store.putConfig(digest, t.project, cfg.governance.configJson(t.project, cfg.roc)).flatMap(_ =>
      appendRecord(iid, ComponentRoc, Surface.Output, outcome, entries, artifactDigest, digest))

  private def output(c: Checked, b: ujson.Obj, reply: ujson.Value, iid: String): GatewayResult =
    val sync = c.t.tier == "Critical"
    roc.evaluate(c.t, b, reply, c.req.evidence, iid, sync, clock()) match
      case Left(why) => rocFailed(c.t, iid, why, readable(reply))
      case Right(o) =>
        appendRoc(c.t, iid, o.outcome, o.entries, o.artifactDigest) match
          case Left(why) => rocFailed(c.t, iid, s"record not stored: $why", Some(o.delivered))
          case Right(_) =>
            if !sync && o.outcome != SafeResponse && cfg.roc.forTemplate(c.t).exists(_.kind == "classifier") then
              background.execute(() => classifyLater(c.t, b, o.delivered, iid))
            o.outcome match
              case "block" => Blocked(iid, o.blockedBy.headOption.getOrElse("roc_blocked"))
              case outcome => Delivered(iid, outcome, o.delivered)

  /** Stage 2 for a delivered non-Critical output: a second ROC record with the classifier entries, `flag` when any
    * score is at or over τ. A failure is a degraded record with the tier's posture; the output has already gone. */
  private def classifyLater(t: Template, b: ujson.Obj, delivered: ujson.Value, iid: String): Unit =
    val recorded =
      try
        for
          out <- ModelOutput.of(delivered)
          entries <- roc.classifyLater(t, b, delivered, iid, clock())
          _ <- appendRoc(t, iid, if entries.forall(_.satisfied) then "pass" else "flag", entries, Canonical.sha256(out.text))
        yield ()
      catch case NonFatal(e) => Left(s"${e.getClass.getName}: ${e.getMessage}")
    recorded.left.foreach { why =>
      System.err.println(s"CLAD_GATEWAY_ROC_FAILED iid=$iid template=${t.id} background: $why")
      degrade(t, iid, s"roc: background classifier: $why")
    }

  /** route_valid and the other deterministic constraints on a delivered passthrough reply. An invalid reply becomes
    * `{}` with status 200, so the caller falls back to its default (spec 8.2). */
  private def passthroughOutput(t: Template, iid: String, status: Int, text: String): GatewayResult =
    val unchecked = ujson.Obj("status" -> status, "body" -> text)
    roc.evaluatePassthrough(t, text, clock()) match
      case Left(why) => rocFailed(t, iid, why, Some(unchecked))
      case Right(o) =>
        val evaluated = if o.outcome == SafeResponse then ujson.Obj("status" -> HttpOk, "body" -> Roc.EmptyAnswer) else unchecked
        appendRoc(t, iid, o.outcome, o.entries, o.artifactDigest) match
          case Left(why) => rocFailed(t, iid, s"record not stored: $why", Some(evaluated))
          case Right(_)  => Delivered(iid, o.outcome, evaluated)

  def passthrough(name: String, path: String, templateId: String, principal: String, session: Option[String],
                  body: String): GatewayResult =
    val iid = InteractionId.generate().value
    try
      cfg.templates.get(templateId) match
        case None => Rejected(s"unknown template $templateId")
        case Some(t) if !t.passthrough.contains(name) =>
          Rejected(s"template $templateId does not declare passthrough to $name")
        case Some(t) =>
          pseudonym(principal) match
            case Left(why) => Rejected(why)
            case Right(p) =>
              val c = Checked(GovernedRequest(templateId, principal, session, Vector.empty, Map.empty, None, None, ujson.Obj()),
                t, p, t.model, "", Vector.empty)
              guarded(c, iid) {
                register(c, iid, clock()) match
                  case Left(_) => Blocked(iid, "gil_unavailable")
                  case Right(_) =>
                    val reply =
                      try forwarder.forward(name, path, body, iid)
                      catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")
                    reply match
                      case Right((status, _)) if status / 100 == ServerErrorClass || status == TooManyRequests =>
                        fail(t, iid, s"HTTP $status")
                      case Right((status, text)) if status / 100 == SuccessClass => passthroughOutput(t, iid, status, text)
                      case Right((status, text)) => Delivered(iid, "pass", ujson.Obj("status" -> status, "body" -> text))
                      case Left(why)             => fail(t, iid, why)
              }
    catch case NonFatal(e) =>
      System.err.println(s"CLAD_GATEWAY_INTERNAL_ERROR iid=$iid ${e.getClass.getName}: ${e.getMessage}")
      Unavailable(Some(iid), InternalError)

  def contain(project: String, reason: String): Either[String, Unit] = update(project, Some(reason))
  /** The containment file is readable; the projects currently contained. */
  def containedProjects: Either[String, Vector[String]] = Lock.synchronized(readContainment()).map(_.keys.toVector.sorted)
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
