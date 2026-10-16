package clad.conformance

import clad.audit.AuditRecordCodec
import clad.conformance.checks.RocChecks
import clad.evaluation.ClassifierDetail
import clad.integrity.*
import clad.runtime.EvaluabilityClass
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class RocChecksSpec extends AnyFunSuite with Matchers:
  private val id = "0192f0c0-0000-7000-8000-000000000001"
  private def run(b: TestBundles.Built) = RocChecks.all(BundleReader.read(b.dir).toOption.get).map(r => r.id -> r).toMap
  private def withRoc(b: TestBundles.Built, roc: clad.runtime.AuditRecord): Unit =
    TestBundles.writeLines(b.dir, "audit.jsonl", TestBundles.chain(b.components,
      Vector(TestBundles.epgRecord(id, TestBundles.at(10), satisfied = true, "pass"), roc)).map(AuditRecordCodec.encode))

  test("valid bundle"):
    val r = run(TestBundles.valid())
    Seq("R-ROC-PRESENT", "R-ROC-DECISION", "R-ROC-THRESH").foreach(x => withClue(r(x).reason)(r(x).verdict shouldBe Verdict.Satisfied))
    r("R-POSTURE").verdict shouldBe Verdict.NotRun

  test("a Critical deterministic failure delivered as pass violates R-ROC-DECISION"):
    val b = TestBundles.valid()
    withRoc(b, TestBundles.rocRecord(id, TestBundles.at(12), "pass",
      Vector(TestBundles.entry("numbers_grounded", ob = true, satisfied = false, TestBundles.at(12)))))
    run(b)("R-ROC-DECISION").verdict shouldBe Verdict.Violated

  test("a Critical deterministic failure with a fallback is fine"):
    val b = TestBundles.valid()
    withRoc(b, TestBundles.rocRecord(id, TestBundles.at(12), "fallback:safe_response",
      Vector(TestBundles.entry("numbers_grounded", ob = true, satisfied = false, TestBundles.at(12)))))
    run(b)("R-ROC-DECISION").verdict shouldBe Verdict.Satisfied

  test("a classifier score over threshold delivered as pass violates R-ROC-DECISION"):
    val b = TestBundles.valid()
    withRoc(b, TestBundles.rocRecord(id, TestBundles.at(12), "pass",
      Vector(TestBundles.entry("unsafe_content", ob = false, satisfied = true, TestBundles.at(12), EvaluabilityClass.Classifier,
        ClassifierDetail("lg", "4", 0.8, 0.5, "1")))))
    run(b)("R-ROC-DECISION").verdict shouldBe Verdict.Violated

  test("a 2xx model call with no ROC record violates R-ROC-PRESENT"):
    val b = TestBundles.valid()
    TestBundles.writeLines(b.dir, "audit.jsonl", TestBundles.chain(b.components,
      Vector(TestBundles.epgRecord(id, TestBundles.at(10), satisfied = true, "pass"))).map(AuditRecordCodec.encode))
    run(b)("R-ROC-PRESENT").verdict shouldBe Verdict.Violated

  test("a project threshold above the enterprise one violates R-ROC-THRESH"):
    val b = TestBundles.valid()
    val p = b.dir.resolve(s"configs/${TestBundles.configDigest}.json")
    java.nio.file.Files.writeString(p, java.nio.file.Files.readString(p).replace("\"flagThreshold\": 0.4", "\"flagThreshold\": 0.7"))
    run(b)("R-ROC-THRESH").verdict shouldBe Verdict.Violated

  test("degraded records: right posture satisfies, wrong posture violates, bad signature violates"):
    def degraded(b: TestBundles.Built, posture: FailurePosture, action: FailureAction, signer: clad.audit.KeyManagementService): Unit =
      val d = DegradedAuditRecord(InteractionId(id), TestBundles.at(12), ComponentId.Roc, DegradedStatus.Degraded, "timeout", posture, action)
      TestBundles.writeLines(b.dir, "degraded.jsonl", Vector(DegradedRecordCodec.encode(SignedDegradedRecord.sign(d, signer, ComponentId.Supervisor).toOption.get)))
    val ok = TestBundles.valid(); degraded(ok, FailurePosture.FailClosed, FailureAction.Blocked, ok.supervisor)
    run(ok)("R-POSTURE").verdict shouldBe Verdict.Satisfied
    val wrong = TestBundles.valid(); degraded(wrong, FailurePosture.FailOpenFlagged, FailureAction.ProceededFlagged, wrong.supervisor)
    run(wrong)("R-POSTURE").verdict shouldBe Verdict.Violated
    val forged = TestBundles.valid(); degraded(forged, FailurePosture.FailClosed, FailureAction.Blocked, forged.components)
    run(forged)("R-POSTURE").verdict shouldBe Verdict.Violated
