package clad.gateway.roc

import clad.core.{Constraint, Level, PropertyId}
import clad.evaluation.{ClassifierDetail, MechanicalDetail}
import clad.gateway.config.{Canonical, GatewayConfig, Template}
import clad.runtime.{AuditEntry, EvaluabilityClass}
import java.time.Instant

/** What the ROC decided for one model output.
  *
  * `entries` are the evaluation of the blocked output followed, for `fallback:redacted` and `fallback:retry`, by the
  * evaluation of the output delivered instead (so a record that names a fallback always carries the unsatisfied entry
  * that caused it, as R-ROC-DECISION requires). `artifactDigest` is the sha256 of the model's output text as evaluated.
  * `blockedBy` lists the properties the first evaluation found unsatisfied (empty for `pass`). */
final case class RocOutcome(outcome: String /* pass|flag|block|fallback:redacted|fallback:retry|fallback:safe_response */,
                            entries: Vector[AuditEntry], delivered: ujson.Value, artifactDigest: String, blockedBy: Vector[String])

/** Runtime output controls (spec 8.3): deterministic checks, the classifier, the decision and the fallbacks. Every
  * number comes from the adopter's runtime.yaml and roc.yaml. `retry` is the upstream call for a retry, made within the
  * same interaction. */
final class Roc(cfg: GatewayConfig, guard: Guard, retry: (ujson.Obj, String) => Either[String, ujson.Value]):
  private val roc = cfg.roc
  private val runtime = cfg.runtime
  private val gov = cfg.governance
  private val Grounding = Set("numbers_grounded", "extraction_grounded")

  /** One evaluation of one output: entries in config order, and the properties found unsatisfied. */
  private final case class Evaluation(entries: Vector[AuditEntry], failed: Vector[String], overTau: Vector[String]):
    def unsatisfied: Vector[String] = failed ++ overTau

  private val Empty = Evaluation(Vector.empty, Vector.empty, Vector.empty)

  private def critical(t: Template): Boolean = t.tier == "Critical"

  /** Left is a component failure (an unreadable reply, a checker or classifier error, or a Critical template asked to
    * run without its synchronous classifier); the caller fails per posture. */
  def evaluate(t: Template, request: ujson.Obj, reply: ujson.Value, evidence: Option[ujson.Value], iid: String,
               classifierSync: Boolean, at: java.time.Instant): Either[String, RocOutcome] =
    for
      _ <- Either.cond(classifierSync || !critical(t), (),
        s"roc: ${t.id} is Critical and its classifier must run synchronously (spec 8.3)")
      out <- ModelOutput.of(reply)
      first <- stages(t, request, out, evidence, iid, classifierSync, at)
      result <- decide(t, request, reply, out, evidence, iid, classifierSync, at, first)
    yield result

  /** The classifier entries alone, for a Standard output already delivered (stage 2 run after the response). */
  def classifyLater(t: Template, request: ujson.Obj, reply: ujson.Value, iid: String,
                    at: java.time.Instant): Either[String, Vector[AuditEntry]] =
    ModelOutput.of(reply).flatMap(classify(t, request, _, iid, at)).map(_.entries)

  /** A passthrough reply (router.jev): the template's deterministic constraints on the reply body, with no request,
    * evidence or classifier. Any unsatisfied constraint is `fallback:safe_response`, delivered as the empty answer `{}`
    * that the caller falls back from (spec 8.2). A passthrough template a classifier applies to is a Left: nothing here
    * can run it, and the startup check refuses such a configuration. */
  def evaluatePassthrough(t: Template, body: String, at: Instant): Either[String, RocOutcome] =
    val classifiers = roc.forTemplate(t).filter(_.kind == "classifier").map(_.property)
    if classifiers.nonEmpty then
      Left(s"roc: ${t.id} is a passthrough and cannot run its classifier constraints ${classifiers.mkString(", ")}")
    else
      deterministic(t, ujson.Obj(), ModelOutput(body, Vector.empty), None, at).map { e =>
        val digest = Canonical.sha256(body)
        if e.failed.isEmpty then RocOutcome("pass", e.entries, ujson.Str(body), digest, Vector.empty)
        else RocOutcome("fallback:safe_response", e.entries, ujson.Str(Roc.EmptyAnswer), digest, e.failed)
      }

  /** Stage 3. A deterministic failure blocks at every tier: spec 8.3 names Critical, and for Standard the only other
    * reading (pass) would deliver an unsatisfied entry as pass. A score at or over τ flags Standard and blocks Critical. */
  private def decision(t: Template, e: Evaluation): String =
    if e.failed.nonEmpty then "block"
    else if e.overTau.nonEmpty then (if critical(t) then "block" else "flag")
    else "pass"

  /** Stages 1 and 2. For Critical the first unsatisfied deterministic check ends the evaluation (straight to stage 4). */
  private def stages(t: Template, request: ujson.Obj, out: ModelOutput, evidence: Option[ujson.Value], iid: String,
                     sync: Boolean, at: Instant): Either[String, Evaluation] =
    deterministic(t, request, out, evidence, at).flatMap { det =>
      if !sync || (critical(t) && det.failed.nonEmpty) then Right(det)
      else classify(t, request, out, iid, at).map(c => Evaluation(det.entries ++ c.entries, det.failed, c.overTau))
    }

  private def constraintOf(c: OutputConstraintDef, level: Level): Either[String, Constraint] =
    PropertyId(c.property).left.map(e => s"roc: invalid property ${e.value}")
      .map(p => if c.ob then Constraint.Obligation(p, level) else Constraint.Prohibition(p, level))

  private def messages(request: ujson.Obj): Vector[ujson.Obj] =
    request.value.get("messages").flatMap(_.arrOpt).map(_.toVector.collect { case o: ujson.Obj => o }).getOrElse(Vector.empty)

  private def requestTools(request: ujson.Obj): Vector[String] =
    request.value.get("tools").flatMap(_.arrOpt).map(_.toVector.flatMap { t =>
      val fn = t.objOpt.flatMap(_.get("function")).filter(_.objOpt.isDefined).getOrElse(t)
      fn.objOpt.flatMap(_.get("name")).flatMap(_.strOpt)
    }).getOrElse(Vector.empty)

  private def responseSchema(request: ujson.Obj): Option[ujson.Value] =
    request.value.get("response_format").flatMap(_.objOpt).flatMap(_.get("json_schema")).flatMap(_.objOpt).flatMap(_.get("schema"))

  /** Deterministic entries: level Enterprise (they are the enterprise's output controls), version roc.version. A
    * constraint with nothing to check against (no evidence, no schema) is recorded satisfied with nothing detected:
    * an obligation satisfied without its property detected is how the entry says "not applicable". */
  private def deterministic(t: Template, request: ujson.Obj, out: ModelOutput, evidence: Option[ujson.Value],
                            at: Instant): Either[String, Evaluation] =
    val ctx = OutputContext(out.content, out.toolCalls, evidence, requestTools(request), responseSchema(request), t.id)
    roc.forTemplate(t).filter(_.kind == "deterministic").foldLeft[Either[String, Evaluation]](Right(Empty)) { (acc, c) =>
      acc.flatMap { e =>
        if critical(t) && e.failed.nonEmpty then Right(e)
        else
          val applicable = Checkers.applicable(c.property, ctx)
          for
            constraint <- constraintOf(c, Level.Enterprise)
            present <- if applicable then Checkers.check(c.property, ctx, runtime, roc, gov) else Right(false)
          yield
            val satisfied = !applicable || present == c.ob
            Evaluation(e.entries :+ AuditEntry(constraint, roc.version, EvaluabilityClass.Mechanical, satisfied, MechanicalDetail(present), at),
              if satisfied then e.failed else e.failed :+ c.property, e.overTau)
      }
    }

  /** What the classifier reads: the last user turn and the output. */
  private def conversation(request: ujson.Obj, out: ModelOutput): Vector[ujson.Obj] =
    messages(request).filter(_.value.get("role").contains(ujson.Str("user"))).lastOption.toVector
      .map(u => ujson.Obj("role" -> "user", "content" -> u.value.getOrElse("content", ujson.Str("")))) :+
      ujson.Obj("role" -> "assistant", "content" -> out.text)

  private def levelOf(th: ThresholdDef): Level = th.level match
    case "enterprise" => Level.Enterprise
    case "department" => Level.Department
    case _            => Level.Project

  /** Classifier entries. A constraint with a category reads that category's score; one without reads P(unsafe). A
    * category the model did not name is held to P(unsafe), the upper bound the model's own probabilities give. The
    * entry is satisfied iff score < τ, with τ, its level and version from the threshold that applies (tau_max if none). */
  private def classify(t: Template, request: ujson.Obj, out: ModelOutput, iid: String, at: Instant): Either[String, Evaluation] =
    val cs = roc.forTemplate(t).filter(_.kind == "classifier")
    if cs.isEmpty then Right(Empty)
    else
      guard.score(conversation(request, out), iid).left.map(e => s"roc: classifier ${roc.classifier.id}: $e").flatMap { s =>
        cs.foldLeft[Either[String, Vector[AuditEntry]]](Right(Vector.empty)) { (acc, c) =>
          acc.flatMap { es =>
            val score = c.category.fold(s.unsafe)(k => s.categories.getOrElse(k, s.unsafe))
            val th = roc.threshold(c.property, t.project, gov)
            val tau = th.map(_.tau).getOrElse(roc.tauMax)
            constraintOf(c, th.map(levelOf).getOrElse(Level.Enterprise)).map(con => es :+ AuditEntry(con, roc.version,
              EvaluabilityClass.Classifier, score < tau,
              ClassifierDetail(roc.classifier.id, roc.classifier.version, score, tau, th.map(_.version).getOrElse(roc.version)), at))
          }
        }.map(es => Evaluation(es, Vector.empty, es.filterNot(_.satisfied).map(_.constraint.property.value)))
      }

  private def decide(t: Template, request: ujson.Obj, reply: ujson.Value, out: ModelOutput, evidence: Option[ujson.Value],
                     iid: String, sync: Boolean, at: Instant, first: Evaluation): Either[String, RocOutcome] =
    val digest = Canonical.sha256(out.text)
    def outcome(name: String, delivered: ujson.Value, after: Vector[AuditEntry] = Vector.empty) =
      RocOutcome(name, first.entries ++ after, delivered, digest, first.unsatisfied)
    def safe: Either[String, RocOutcome] = safeResponse(t, reply, evidence).map(outcome("fallback:safe_response", _))
    decision(t, first) match
      case "block" if first.failed == Vector("contains_pii") && first.overTau.isEmpty =>
        gov.checkers.get("contains_pii").toRight("roc: contains_pii: no checker is defined in constraints.yaml")
          .flatMap(ch => Fallbacks.redact(out.content, ch.patterns)).flatMap { redacted =>
            val r = out.copy(content = redacted, nullContent = false)
            stages(t, request, r, evidence, iid, sync, at).flatMap { e =>
              if decision(t, e) == "pass" then
                Fallbacks.deliver(reply, r).map(outcome("fallback:redacted", _, e.entries))
              else safe
            }
          }
      case "block" if first.failed.exists(Grounding.contains) =>
        retries(t, request, evidence, iid, sync, at, Vector(out.text), 0).flatMap {
          case Some((r, o, e)) => Fallbacks.deliver(r, o).map(outcome("fallback:retry", _, e.entries))
          case None         => safe
        }
      case "block" => safe
      case other   => Fallbacks.deliver(reply, out).map(outcome(other, _))

  /** Up to max_retries retries, each the same request plus the variant line. A failed retry whose output is more than
    * retry_similarity similar to an earlier failed output ends the retries: calling again would not help. A retry
    * whose upstream call fails ends them too, and the safe response is delivered, never the unchecked output. */
  private def retries(t: Template, request: ujson.Obj, evidence: Option[ujson.Value], iid: String, sync: Boolean,
                      at: Instant, failedOutputs: Vector[String], attempt: Int): Either[String, Option[(ujson.Value, ModelOutput, Evaluation)]] =
    if attempt >= runtime.maxRetries then Right(None)
    else retry(Fallbacks.withVariant(request, roc.retryVariant), iid).flatMap(r => ModelOutput.of(r).map(r -> _)) match
      case Left(err) =>
        System.err.println(s"ROC_RETRY_FAILED iid=$iid template=${t.id} $err")
        Right(None)
      case Right((r, o)) =>
        stages(t, request, o, evidence, iid, sync, at).flatMap { e =>
          if decision(t, e) == "pass" then Right(Some((r, o, e)))
          else if failedOutputs.exists(Fallbacks.similarity(o.text, _) > runtime.retrySimilarity) then Right(None)
          else retries(t, request, evidence, iid, sync, at, failedOutputs :+ o.text, attempt + 1)
        }

  /** The governed safe response: the template's, else the default; for an answer over evidence rows, the rows follow
    * as a table. It is a fresh choice: nothing of the blocked message (text, tool calls, reasoning, logprobs) remains. */
  private def safeResponse(t: Template, reply: ujson.Value, evidence: Option[ujson.Value]): Either[String, ujson.Value] =
    roc.safeResponses.get(t.id).orElse(roc.safeResponses.get("default"))
      .toRight(s"roc.yaml: no safe response for ${t.id} and no default").flatMap { base =>
        val rows = if t.evidenceFormat == "rows_json" then evidence.flatMap(Fallbacks.table) else None
        Fallbacks.safe(reply, rows.fold(base)(tb => base + "\n\n" + tb))
      }

object Roc:
  /** What a blocked passthrough delivers: an empty JSON object, which the caller treats as no answer. */
  val EmptyAnswer = "{}"
