package clad.conformance

import clad.conformance.checks.PrivacyChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class PrivacyChecksSpec extends AnyFunSuite with Matchers:
  private def run(b: TestBundles.Built) = PrivacyChecks.all(BundleReader.read(b.dir).toOption.get).head

  test("digests, UUIDs and signatures are not PII"):
    run(TestBundles.valid()).verdict shouldBe Verdict.Satisfied

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
