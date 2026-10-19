package clad.gateway.pipeline

import clad.audit.Ed25519KeyManagement
import clad.core.Surface
import clad.evaluation.ClassifierDetail
import clad.gateway.config.{Fixtures, GatewayConfig}
import clad.gateway.epg.Epg
import clad.gateway.roc.{Guard, LlamaGuard}
import clad.gateway.store.MemoryStore
import clad.integrity.{ComponentId, FailureAction, FailurePosture}
import clad.runtime.{AuditRecord, EvaluabilityClass}
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import java.time.Instant

class GatewayRocSpec extends AnyFunSuite:
  private val (components, _) = Ed25519KeyManagement.generate("c0mp0nents000001")
  private val (supervisor, _) = Ed25519KeyManagement.generate("5upervis0r000001")
  private val now = Instant.parse("2026-10-17T12:00:00Z")

  private final class Rig(up: FakeUpstream = FakeUpstream(), val guard: Guard = FixedGuard(Right(guardScores(0.01))),
                          pass: FakePassthrough = FakePassthrough()):
    val cfg: GatewayConfig = Fixtures.approvedConfig("assistant.general")
    val store = MemoryStore()
    val upstream: FakeUpstream = up
    val background = QueuedExecutor()
    val gw = Gateway(cfg, Epg.build(cfg).fold(e => throw AssertionError(e), identity), store, components, supervisor, up,
      pass, Files.createTempDirectory("contain").resolve("contained.json"), () => now, guard, background)
    def records(iid: String, component: String): Vector[AuditRecord] =
      store.lines.toVector.map(_.record).filter(r => r.interactionId.map(_.value).contains(iid) && r.component.contains(component))

  private val rows = ujson.read("""[{"scenario": "base", "tax_drag_ratio": 0.0666328857}]""")
  private val grounded = "The base scenario's tax drag is 6.66 %."
  private val ungrounded = "The base scenario's tax drag is 9.99 %."

  private def delivered(r: GatewayResult): (String, String, ujson.Value) = r match
    case GatewayResult.Delivered(i, o, v) => (i, o, v)
    case other                            => fail(s"expected Delivered, got $other")

  private def content(v: ujson.Value): String = v("choices")(0)("message")("content").str

  private def assistantRequest: GovernedRequest =
    GovernedRequest("assistant.general", "api", None, Vector(ujson.Obj("role" -> "user", "content" -> "hello")),
      Map.empty, None, None, ujson.Obj())

  test("a delivered Critical call has exactly one EPG and one ROC record, the ROC record Output with its config stored") {
    val rig = Rig(FakeUpstream(Right(ok(grounded))))
    val (iid, outcome, v) = delivered(rig.gw.complete(answerRequest(evidence = Some(rows))))
    assert(outcome == "pass" && content(v) == grounded)
    assert(rig.records(iid, "epg").size == 1)
    val roc = rig.records(iid, "roc")
    assert(roc.size == 1 && roc.head.surface.contains(Surface.Output) && roc.head.outcome.contains("pass"))
    assert(roc.head.entries.exists(_.evaluabilityClass == EvaluabilityClass.Classifier), "Critical classifies before delivery")
    assert(rig.store.configs.contains(roc.head.configDigest))
    assert(rig.background.pending == 0)
  }

  test("an ungrounded Critical answer is retried within the same interaction and the retry is delivered") {
    val rig = Rig(FakeUpstream(Right(ok(ungrounded)), Right(ok(grounded))))
    val (iid, outcome, v) = delivered(rig.gw.complete(answerRequest(evidence = Some(rows))))
    assert(outcome == "fallback:retry" && content(v) == grounded)
    assert(rig.upstream.calls.map(_._2).toSeq == Seq(iid, iid))
    assert(rig.upstream.calls(1)._1("messages").arr.last("content").str == rig.cfg.roc.retryVariant)
    assert(rig.store.gil.size == 1 && rig.records(iid, "roc").map(_.outcome) == Vector(Some("fallback:retry")))
  }

  test("a classifier failure on Critical blocks with a supervisor-signed degraded record and delivers nothing") {
    val rig = Rig(FakeUpstream(Right(ok(grounded))), FixedGuard(Left("timeout")))
    val iid = rig.gw.complete(answerRequest(evidence = Some(rows))) match
      case GatewayResult.Blocked(i, "roc_unavailable") => i
      case other                                       => fail(s"got $other")
    assert(rig.records(iid, "roc").isEmpty)
    val d = rig.store.degradedLines.toVector
    assert(d.size == 1 && d.head.record.component == ComponentId.Roc && d.head.record.postureApplied == FailurePosture.FailClosed)
    assert(d.head.record.actionTaken == FailureAction.Blocked && d.head.record.failureReason.contains("timeout"))
  }

  test("Standard: a deterministic ROC record on return, then a classifier record once the background task runs") {
    val guard = FixedGuard(Right(guardScores(0.01)))
    val rig = Rig(FakeUpstream(Right(ok("Hello there."))), guard)
    val (iid, outcome, _) = delivered(rig.gw.complete(assistantRequest))
    assert(outcome == "pass" && guard.calls.isEmpty && rig.background.pending == 1)
    val first = rig.records(iid, "roc")
    assert(first.size == 1 && first.head.entries.forall(_.evaluabilityClass == EvaluabilityClass.Mechanical))
    rig.background.runAll()
    val both = rig.records(iid, "roc")
    assert(both.size == 2 && both(1).outcome.contains("pass") && guard.calls.toSeq == Seq(iid))
    assert(both(1).entries.nonEmpty && both(1).entries.forall(_.evaluabilityClass == EvaluabilityClass.Classifier))
    assert(both(1).artifactDigest == both(0).artifactDigest)
  }

  test("Standard: a background score at or over tau writes a flag record; the delivered reply is unchanged") {
    val rig = Rig(FakeUpstream(Right(ok("Hello there."))), FixedGuard(Right(guardScores(0.9))))
    val (iid, outcome, v) = delivered(rig.gw.complete(assistantRequest))
    assert(outcome == "pass" && content(v) == "Hello there.")
    rig.background.runAll()
    val later = rig.records(iid, "roc")(1)
    assert(later.outcome.contains("flag") && later.entries.exists(e => e.detail match
      case ClassifierDetail(_, _, score, tau, _) => !e.satisfied && score >= tau
      case _                                     => false))
  }

  test("Standard: a background classifier failure is a FailOpenFlagged degraded record") {
    val rig = Rig(FakeUpstream(Right(ok("Hello there."))), FixedGuard(Left("timeout")))
    val (iid, _, _) = delivered(rig.gw.complete(assistantRequest))
    rig.background.runAll()
    assert(rig.records(iid, "roc").size == 1)
    val d = rig.store.degradedLines.toVector
    assert(d.size == 1 && d.head.record.postureApplied == FailurePosture.FailOpenFlagged)
    assert(d.head.record.actionTaken == FailureAction.ProceededFlagged)
  }

  test("router: a valid route is delivered with a ROC record holding route_valid") {
    val rig = Rig()
    val (iid, outcome, v) = delivered(rig.gw.passthrough("jev", "", "router.jev", "api", None, "{}"))
    assert(outcome == "pass" && v("body").str == ValidRoute)
    val roc = rig.records(iid, "roc")
    assert(roc.size == 1 && roc.head.outcome.contains("pass"))
    assert(roc.head.entries.exists(e => e.constraint.property.value == "route_valid" && e.satisfied))
  }

  test("router: an unknown intent is {} with status 200 and outcome fallback:safe_response") {
    val rig = Rig(pass = FakePassthrough("""{"answers": {"intent": {"choice": "launch_missiles"}}}"""))
    val (iid, outcome, v) = delivered(rig.gw.passthrough("jev", "", "router.jev", "api", None, "{}"))
    assert(outcome == "fallback:safe_response" && v("status").num == 200 && v("body").str == "{}")
    val roc = rig.records(iid, "roc")
    assert(roc.map(_.outcome) == Vector(Some("fallback:safe_response")))
    assert(roc.head.entries.exists(e => e.constraint.property.value == "route_valid" && !e.satisfied))
  }

  test("the classifier call reaches its upstream with the call's interaction id") {
    val verdict = ujson.Obj("choices" -> ujson.Arr(ujson.Obj(
      "message" -> ujson.Obj("role" -> "assistant", "content" -> "safe"),
      "logprobs" -> ujson.Obj("content" -> ujson.Arr(ujson.Obj("token" -> "safe", "logprob" -> -0.001))))))
    val classifierUp = FakeUpstream(Right(verdict))
    val cfg = Fixtures.approvedConfig()
    val rig = Rig(FakeUpstream(Right(ok(grounded))), LlamaGuard(classifierUp, cfg.roc.classifier))
    val (iid, outcome, _) = delivered(rig.gw.complete(answerRequest(evidence = Some(rows))))
    assert(outcome == "pass")
    assert(classifierUp.calls.map(_._2).toSeq == Seq(iid))
    assert(classifierUp.calls.head._1("model").str == cfg.roc.classifier.model)
  }

  test("Standard: a reply the ROC cannot read is a FailOpenFlagged degraded record and nothing unread is delivered") {
    val unreadable = ujson.Obj("choices" -> ujson.Arr(ujson.Obj("message" -> ujson.Obj("role" -> "assistant", "content" -> 42))))
    val rig = Rig(FakeUpstream(Right(unreadable)))
    rig.gw.complete(assistantRequest) match
      case GatewayResult.Unavailable(Some(iid), "roc_unavailable") => assert(rig.records(iid, "roc").isEmpty)
      case other                                                    => fail(s"got $other")
    val d = rig.store.degradedLines.toVector
    assert(d.size == 1 && d.head.record.actionTaken == FailureAction.ProceededFlagged && rig.background.pending == 0)
  }

  test("Critical: a reply the ROC cannot read blocks with roc_unavailable") {
    val unreadable = ujson.Obj("choices" -> ujson.Arr(ujson.Obj("message" -> ujson.Obj("role" -> "assistant", "content" -> 42))))
    val rig = Rig(FakeUpstream(Right(unreadable)))
    assert(rig.gw.complete(answerRequest()).isInstanceOf[GatewayResult.Blocked])
    assert(rig.store.degradedLines.map(_.record.actionTaken).toSeq == Seq(FailureAction.Blocked))
  }
