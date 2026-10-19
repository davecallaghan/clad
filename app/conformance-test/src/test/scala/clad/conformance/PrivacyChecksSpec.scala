package clad.conformance

import clad.conformance.checks.PrivacyChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PrivacyChecksSpec extends AnyFunSuite with Matchers:
  private def run(b: TestBundles.Built) = PrivacyChecks.all(BundleReader.read(b.dir).toOption.get).head

  test("digests, UUIDs and signatures are not PII"):
    run(TestBundles.valid()).verdict shouldBe Verdict.Satisfied

  test("a classifier score with long decimals is not an account number; the same digits elsewhere are"):
    val score = clad.evaluation.ClassifierDetail("llama_guard", "4", 0.008577522801712312, 0.5, "1")
    val roc = TestBundles.rocRecord("0192f0c0-0000-7000-8000-000000000001", TestBundles.at(12), "pass", Vector(TestBundles.entry(
      "unsafe_content", ob = false, satisfied = true, TestBundles.at(12), clad.runtime.EvaluabilityClass.Classifier, score)))
    val b = TestBundles.valid()
    TestBundles.writeLines(b.dir, "audit.jsonl", TestBundles.chain(b.components, Vector(roc)).map(clad.audit.AuditRecordCodec.encode))
    run(b).verdict shouldBe Verdict.Satisfied
    val c = TestBundles.valid()
    TestBundles.rewrite(c.dir, "gil.jsonl")(ls => ls.map(_.replace("\"tier\":\"Critical\"", "\"tier\":\"Critical\",\"note\":\"0.008577522801712312\"")))
    run(c).verdict shouldBe Verdict.Violated

  test("an SSN in GIL metadata violates R-AI6"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "gil.jsonl")(ls => ls.map(_.replace("\"tier\":\"Critical\"", "\"tier\":\"Critical\",\"note\":\"123-45-6789\"")))
    run(b).verdict shouldBe Verdict.Violated

  test("an email in an alert summary violates R-AI6"):
    val b = TestBundles.valid()
    TestBundles.writeLines(b.dir, "mdr_alerts.jsonl", Vector("""{"alertId":"a","raisedAt":"2026-10-15T10:01:00Z","severity":"P3","taxonomy":"governance","detector":"x","interactionIds":[],"summary":"user jane@example.com"}"""))
    run(b).verdict shouldBe Verdict.Violated

  test("an extra field in an audit record (content smuggled in) violates R-AI6"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "audit.jsonl")(ls => ls.map(_.replaceFirst("\\{\"record\":\\{", "{\"record\":{\"content\":\"hello\",")))
    run(b).verdict shouldBe Verdict.Violated
