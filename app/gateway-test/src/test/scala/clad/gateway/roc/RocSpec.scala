package clad.gateway.roc

import clad.audit.{Signature, SignedAuditRecord}
import clad.conformance.*
import clad.conformance.checks.RocChecks
import clad.core.{InteractionId, Surface}
import clad.evaluation.{ClassifierDetail, MechanicalDetail}
import clad.gateway.config.*
import clad.integrity.GilEntry
import clad.runtime.{AuditEntry, AuditRecord, EvaluabilityClass}
import org.scalatest.funsuite.AnyFunSuite
import java.time.Instant
import scala.collection.mutable.ArrayBuffer

class RocSpec extends AnyFunSuite:
  private val cfg: GatewayConfig = Fixtures.config
  private val at = Instant.parse("2026-10-17T10:00:00Z")
  private def template(id: String): Template = cfg.templates.get(id).getOrElse(fail(s"no template $id"))

  /** Scores the fake classifier returns: every category at `unsafe` unless overridden. */
  private def scores(unsafe: Double, categories: (String, Double)*): GuardScores =
    GuardScores(unsafe, LlamaGuard.SafeCategories.map(_ -> unsafe).toMap ++ categories.toMap, if categories.isEmpty then "safe" else "unsafe")

  private final class FakeGuard(result: Either[String, GuardScores]) extends Guard:
    val calls: ArrayBuffer[(Vector[ujson.Obj], String)] = ArrayBuffer.empty
    def score(conversation: Vector[ujson.Obj], interactionId: String): Either[String, GuardScores] =
      calls += ((conversation, interactionId)); result

  /** A fake upstream for retries: answers from `replies` in turn, recording each request. */
  private final class FakeRetry(replies: ujson.Value*):
    val calls: ArrayBuffer[(ujson.Obj, String)] = ArrayBuffer.empty
    def apply(req: ujson.Obj, iid: String): Either[String, ujson.Value] =
      calls += ((req, iid))
      replies.lift(calls.size - 1).toRight("no more fake replies")

  private val low = scores(0.01)
  private def roc(guard: Guard = FakeGuard(Right(low)), retry: FakeRetry = FakeRetry()): Roc = Roc(cfg, guard, retry.apply)

  private def reply(content: String, toolCalls: Vector[ujson.Obj] = Vector.empty): ujson.Value =
    val msg = ujson.Obj("role" -> "assistant", "content" -> content)
    if toolCalls.nonEmpty then msg("tool_calls") = ujson.Arr.from(toolCalls)
    ujson.Obj("id" -> "r1", "choices" -> ujson.Arr(ujson.Obj("index" -> 0, "message" -> msg,
      "finish_reason" -> (if toolCalls.isEmpty then "stop" else "tool_calls"))))

  private def request(question: String, tools: Vector[String] = Vector.empty): ujson.Obj =
    val r = ujson.Obj("model" -> "m", "messages" -> ujson.Arr(ujson.Obj("role" -> "system", "content" -> "sys"),
      ujson.Obj("role" -> "user", "content" -> question)))
    if tools.nonEmpty then r("tools") = ujson.Arr.from(tools.map(n =>
      ujson.Obj("type" -> "function", "function" -> ujson.Obj("name" -> n, "parameters" -> ujson.Obj()))))
    r

  private def content(v: ujson.Value): String = v("choices")(0)("message")("content").str
  private def message(v: ujson.Value): ujson.Obj = v("choices")(0)("message").obj
  private def toolCall(name: String, args: String): ujson.Obj =
    ujson.Obj("id" -> "c1", "type" -> "function", "function" -> ujson.Obj("name" -> name, "arguments" -> args))

  private val rows = ujson.read("""[{"scenario": "base", "tax_drag_ratio": 0.0666328857}, {"scenario": "roth", "tax_drag_ratio": 0.0410256}]""")
  private val grounded = "The base scenario's tax drag is 6.66 %, against 4.10% for roth."
  private val ungrounded = "The base scenario's tax drag is 9.99 %, which is high."

  private def properties(o: RocOutcome): Vector[String] = o.entries.map(_.constraint.property.value)
  private def ok(e: Either[String, RocOutcome]): RocOutcome = e.fold(err => fail(err), identity)

  /** Every outcome the scenarios produce, with the template's project, for the R-ROC-DECISION check. */
  private val produced = ArrayBuffer.empty[(String, Template, RocOutcome)]
  private def record(iid: String, t: Template, o: RocOutcome): RocOutcome = { produced += ((iid, t, o)); o }

  test("a grounded agent answer passes, with an entry per constraint and classifier entries carrying their scores") {
    val t = template("agent.answer")
    val g = FakeGuard(Right(scores(0.02)))
    val o = record("i-pass", t, ok(roc(g).evaluate(t, request("What is the drag?"), reply(grounded), Some(rows), "i-pass", true, at)))
    assert(o.outcome == "pass" && o.blockedBy.isEmpty && content(o.delivered) == grounded)
    assert(properties(o) == Vector("numbers_grounded", "contains_pii", "guarantee_language", "specialized_advice", "unsafe_content"))
    assert(o.entries.forall(_.satisfied) && o.entries.forall(_.constraintVersion == cfg.roc.version))
    val classifier = o.entries.filter(_.evaluabilityClass == EvaluabilityClass.Classifier)
    assert(classifier.map(_.constraint.property.value) == Vector("specialized_advice", "unsafe_content"))
    classifier.foreach(e => e.detail match
      case ClassifierDetail(id, version, score, tau, _) =>
        assert(id == cfg.roc.classifier.id && version == cfg.roc.classifier.version)
        assert(score == 0.02 && tau == cfg.roc.tau(e.constraint.property.value, t.project, cfg.governance))
      case other => fail(s"not a classifier detail: $other"))
    assert(o.entries.filter(_.evaluabilityClass == EvaluabilityClass.Mechanical).map(_.detail)
      .forall(_.isInstanceOf[MechanicalDetail]))
    assert(o.artifactDigest == Canonical.sha256(grounded))
    assert(g.calls.map(_._2) == ArrayBuffer("i-pass") && g.calls.flatMap(_._1).exists(_.value.get("content").contains(ujson.Str(grounded))))
  }

  test("an answer with no evidence records numbers_grounded as not applicable: satisfied, nothing detected") {
    val t = template("agent.answer")
    val o = record("i-na", t, ok(roc().evaluate(t, request("hi"), reply("Hello there."), None, "i-na", true, at)))
    assert(o.outcome == "pass")
    val ng = o.entries.find(_.constraint.property.value == "numbers_grounded").getOrElse(fail("no numbers_grounded entry"))
    assert(ng.satisfied && ng.detail == MechanicalDetail(false))
  }

  test("an ungrounded number is retried with the variant line; a passing retry is delivered as fallback:retry") {
    val t = template("agent.answer")
    val retry = FakeRetry(reply(grounded))
    val req = request("What is the drag?")
    val o = record("i-retry", t, ok(roc(retry = retry).evaluate(t, req, reply(ungrounded), Some(rows), "i-retry", true, at)))
    assert(o.outcome == "fallback:retry" && content(o.delivered) == grounded && o.blockedBy == Vector("numbers_grounded"))
    assert(retry.calls.length == 1 && retry.calls.forall(_._2 == "i-retry"))
    val sent = retry.calls.map(_._1("messages").arr).headOption.getOrElse(fail("no retry"))
    assert(sent.last == ujson.Obj("role" -> "user", "content" -> cfg.roc.retryVariant))
    assert(sent.dropRight(1) == req("messages").arr, "the retry is the same request plus the variant line")
    assert(req("messages").arr.length == sent.length - 1, "the caller's request is not mutated")
  }

  test("an ungrounded retry identical to the failed output stops retrying and gives the safe response with the rows table") {
    val t = template("agent.answer")
    val retry = FakeRetry(reply(ungrounded), reply(ungrounded), reply(ungrounded))
    val o = record("i-similar", t, ok(roc(retry = retry).evaluate(t, request("drag?"), reply(ungrounded), Some(rows), "i-similar", true, at)))
    assert(o.outcome == "fallback:safe_response")
    assert(retry.calls.nonEmpty && retry.calls.length < cfg.runtime.maxRetries, "the similarity skip stops before max_retries")
    val text = content(o.delivered)
    assert(text.startsWith(cfg.roc.safeResponses("agent.answer")))
    assert(text.contains("| scenario | tax_drag_ratio |") && text.contains("| base | 0.0666328857 |") && text.contains("| roth |"))
    assert(!text.contains("9.99") && !text.contains("which is high"))
    assert(o.artifactDigest == Canonical.sha256(ungrounded) && o.entries.exists(!_.satisfied))
  }

  test("retries that keep failing with different outputs stop at max_retries") {
    val t = template("agent.answer")
    // each output shares almost no tokens with the others, and each states a figure the rows do not hold
    val retry = FakeRetry((0 until cfg.runtime.maxRetries + cfg.runtime.maxRetries).map(i => reply(s"token$i other$i 88.8$i %"))*)
    val o = record("i-max", t, ok(roc(retry = retry).evaluate(t, request("drag?"), reply(ungrounded), Some(rows), "i-max", true, at)))
    assert(o.outcome == "fallback:safe_response" && retry.calls.length == cfg.runtime.maxRetries)
  }

  test("a retry whose upstream call fails gives the safe response, never the unchecked output") {
    val t = template("agent.answer")
    val o = record("i-retry-fail", t, ok(roc(retry = FakeRetry()).evaluate(t, request("drag?"), reply(ungrounded), Some(rows), "i-retry-fail", true, at)))
    assert(o.outcome == "fallback:safe_response" && !content(o.delivered).contains("9.99"))
  }

  test("an SSN in an assistant answer is redacted and delivered as fallback:redacted") {
    val t = template("assistant.general")
    val answer = "Your SSN 123-45-6789 is on file."
    val o = record("i-pii", t, ok(roc().evaluate(t, request("my ssn?"), reply(answer), None, "i-pii", false, at)))
    assert(o.outcome == "fallback:redacted" && o.blockedBy == Vector("contains_pii"))
    assert(content(o.delivered) == "Your SSN [redacted] is on file." && !ujson.write(o.delivered).contains("123-45-6789"))
    assert(o.entries.exists(e => e.constraint.property.value == "contains_pii" && !e.satisfied))
    assert(o.entries.exists(e => e.constraint.property.value == "contains_pii" && e.satisfied), "the redacted text's evaluation is recorded")
    assert(o.entries.forall(_.evaluabilityClass == EvaluabilityClass.Mechanical), "Standard without classifierSync runs no classifier")
  }

  test("PII and guarantee language together are not redacted: the safe response") {
    val t = template("assistant.general")
    val o = record("i-pii-guar", t, ok(roc().evaluate(t, request("x"), reply("SSN 123-45-6789, guaranteed returns."), None, "i-pii-guar", false, at)))
    assert(o.outcome == "fallback:safe_response" && content(o.delivered) == cfg.roc.safeResponses("default"))
    assert(o.blockedBy.toSet == Set("contains_pii", "guarantee_language"))
  }

  test("an S6 score over tau flags a Standard answer, unchanged, and blocks a Critical one to the safe response") {
    val s6 = FakeGuard(Right(scores(0.9, "S6" -> 0.9)))
    val std = template("assistant.general")
    val answer = "You should buy fund X."
    val f = record("i-flag", std, ok(roc(s6).evaluate(std, request("what fund?"), reply(answer), None, "i-flag", true, at)))
    assert(f.outcome == "flag" && content(f.delivered) == answer && f.blockedBy.contains("specialized_advice"))
    val crit = template("agent.answer")
    val b = record("i-block", crit, ok(roc(s6).evaluate(crit, request("what fund?"), reply("Buy fund X."), Some(rows), "i-block", true, at)))
    assert(b.outcome == "fallback:safe_response" && !content(b.delivered).contains("fund X"))
    assert(b.entries.exists(e => e.constraint.property.value == "specialized_advice" && !e.satisfied))
  }

  test("classifyLater returns only the classifier entries for a Standard answer") {
    val t = template("assistant.general")
    val es = roc(FakeGuard(Right(scores(0.9, "S6" -> 0.9)))).classifyLater(t, request("q"), reply("Buy X."), "i-later", at)
      .fold(e => fail(e), identity)
    assert(es.map(_.constraint.property.value) == Vector("specialized_advice", "unsafe_content"))
    assert(es.forall(_.evaluabilityClass == EvaluabilityClass.Classifier) && es.exists(!_.satisfied))
    assert(roc(FakeGuard(Left("timeout"))).classifyLater(t, request("q"), reply("x"), "i-later", at).isLeft)
  }

  test("a classifier failure with classifierSync is a Left, never an unscored pass") {
    val t = template("agent.answer")
    val r = roc(FakeGuard(Left("classifier timeout"))).evaluate(t, request("q"), reply(grounded), Some(rows), "i-left", true, at)
    assert(r.left.exists(_.contains("classifier timeout")), r.toString)
  }

  test("a Critical deterministic failure goes straight to the fallback without calling the classifier") {
    val t = template("agent.answer")
    val g = FakeGuard(Right(low))
    val o = record("i-crit", t, ok(roc(g).evaluate(t, request("q"), reply("Guaranteed returns, risk-free."), None, "i-crit", true, at)))
    assert(o.outcome == "fallback:safe_response" && g.calls.isEmpty)
    assert(o.entries.lastOption.exists(e => e.constraint.property.value == "guarantee_language" && !e.satisfied))
  }

  test("planner tool calls naming request tools pass; an unknown tool gives the safe response with no tool calls") {
    val t = template("planner.chat")
    val req = request("add a node", Vector("apply_ops"))
    val good = record("i-tools", t, ok(roc().evaluate(t, req, reply("", Vector(toolCall("apply_ops", """{"ops": []}"""))), None, "i-tools", true, at)))
    assert(good.outcome == "pass" && message(good.delivered).value.contains("tool_calls"))
    val bad = record("i-badtool", t, ok(roc().evaluate(t, req, reply("", Vector(toolCall("rm_rf", "{}"))), None, "i-badtool", true, at)))
    assert(bad.outcome == "fallback:safe_response" && bad.blockedBy == Vector("tool_calls_valid"))
    assert(!message(bad.delivered).value.contains("tool_calls") && content(bad.delivered) == cfg.roc.safeResponses("default"))
    assert(bad.delivered("choices")(0)("finish_reason").str == "stop")
  }

  test("a reply with no message is a component failure") {
    val t = template("assistant.general")
    assert(roc().evaluate(t, request("q"), ujson.Obj("choices" -> ujson.Arr()), None, "i-empty", false, at).isLeft)
  }

  private def bundle(records: Vector[(String, Template, String, Vector[AuditEntry], String)]): Bundle =
    val meta = BundleMeta(1, "gateway-test", Window(at.minusSeconds(60), at.plusSeconds(60)), None,
      cfg.governance.projects.map((n, p) => n -> ProjectSpec(p.tier, p.expectedComponents)), Map.empty)
    val gil = records.map((iid, t, _, _, _) => GilEntry(InteractionId(iid), at, Map("project" -> t.project)))
    val audit = records.map((iid, _, outcome, entries, digest) =>
      SignedAuditRecord(AuditRecord(digest, entries, "sha256:config", at, None, Some(InteractionId(iid)), Some("roc"),
        Some(Surface.Output), Some(outcome)), Signature(Array.emptyByteArray, "k", "none"), None))
    Bundle(meta, Map.empty, Map.empty, Some(gil), None, Some(audit), None, None, None, None, Map.empty, Map.empty)

  private def decision(b: Bundle): RequirementResult =
    RocChecks.all(b).find(_.id == "R-ROC-DECISION").getOrElse(fail("no R-ROC-DECISION"))

  test("R-ROC-DECISION, by conformance's own RocChecks, holds for every outcome the scenarios produced") {
    assert(produced.nonEmpty)
    assert(produced.map(_._3.outcome).toSet == Set("pass", "flag", "fallback:retry", "fallback:redacted", "fallback:safe_response"))
    val r = decision(bundle(produced.toVector.map((iid, t, o) => (iid, t, o.outcome, o.entries, o.artifactDigest))))
    assert(r.verdict == Verdict.Satisfied, r.toString)
    // the check bites: a pass carrying an unsatisfied entry is a violation
    val (iid, t, o) = produced.find(_._3.outcome == "fallback:safe_response").getOrElse(fail("no safe response"))
    assert(decision(bundle(Vector((iid, t, "pass", o.entries, o.artifactDigest)))).verdict == Verdict.Violated)
  }
