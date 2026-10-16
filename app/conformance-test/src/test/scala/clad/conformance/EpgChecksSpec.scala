package clad.conformance

import clad.audit.AuditRecordCodec
import clad.conformance.checks.EpgChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EpgChecksSpec extends AnyFunSuite with Matchers:
  private val id = "0192f0c0-0000-7000-8000-000000000001"
  private def run(b: TestBundles.Built) = EpgChecks.all(BundleReader.read(b.dir).toOption.get).map(r => r.id -> r).toMap
  private def withRecords(b: TestBundles.Built, rs: Vector[clad.runtime.AuditRecord]): Unit =
    TestBundles.writeLines(b.dir, "audit.jsonl", TestBundles.chain(b.components, rs).map(AuditRecordCodec.encode))

  test("valid bundle: re-evaluation agrees and nothing needed blocking"):
    val r = run(TestBundles.valid())
    r("R-EPG-REEVAL").verdict shouldBe Verdict.Satisfied
    r("R-EPG-BLOCK").verdict shouldBe Verdict.Satisfied

  test("a record claiming a different mechanical result violates R-EPG-REEVAL"):
    val b = TestBundles.valid()
    // the prompt has no SSN, so contains_pii is satisfied; claim it was not
    withRecords(b, Vector(TestBundles.epgRecord(id, TestBundles.at(10), satisfied = false, "block")))
    run(b)("R-EPG-REEVAL").verdict shouldBe Verdict.Violated

  test("an unsatisfied entry with outcome pass violates R-EPG-BLOCK"):
    val b = TestBundles.valid()
    withRecords(b, Vector(TestBundles.epgRecord(id, TestBundles.at(10), satisfied = false, "pass"),
                          TestBundles.rocRecord(id, TestBundles.at(12), "pass")))
    run(b)("R-EPG-BLOCK").verdict shouldBe Verdict.Violated

  test("a blocked interaction that still has a ROC record violates R-EPG-BLOCK"):
    val b = TestBundles.valid()
    withRecords(b, Vector(TestBundles.epgRecord(id, TestBundles.at(10), satisfied = false, "block"),
                          TestBundles.rocRecord(id, TestBundles.at(12), "pass")))
    run(b)("R-EPG-BLOCK").verdict shouldBe Verdict.Violated

  test("a procedural attestation by a non-human violates R-EPG-REEVAL"):
    val b = TestBundles.valid()
    val r0 = TestBundles.epgRecord(id, TestBundles.at(10), satisfied = true, "pass")
    val r1 = r0.copy(entries = r0.entries.map(e => e.detail match
      case clad.evaluation.ProceduralDetail(_, at, why) => e.copy(detail = clad.evaluation.ProceduralDetail("cline", at, why))
      case _ => e))
    withRecords(b, Vector(r1, TestBundles.rocRecord(id, TestBundles.at(12), "pass")))
    run(b)("R-EPG-REEVAL").verdict shouldBe Verdict.Violated
