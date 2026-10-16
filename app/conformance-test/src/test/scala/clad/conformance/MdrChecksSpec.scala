package clad.conformance

import clad.conformance.checks.MdrChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MdrChecksSpec extends AnyFunSuite with Matchers:
  private val cfg = Some(MdrConfig(heartbeatIntervalSeconds = 30, detectionDelaySeconds = 120))
  private def run(b: TestBundles.Built, c: Option[MdrConfig] = cfg) = MdrChecks.all(BundleReader.read(b.dir).toOption.get, c).map(r => r.id -> r).toMap
  private val ghostInfra = """{"ts":"2026-10-15T10:02:00Z","host":"api.deepinfra.com","method":"POST","path":"/x","status":401,"interactionId":null}"""

  test("no ghosts and continuous heartbeats"):
    val r = run(TestBundles.valid())
    r("R-MDR-ALERT").verdict shouldBe Verdict.Satisfied
    r("R-MDR-LIVE").verdict shouldBe Verdict.Satisfied

  test("an unalerted ghost violates R-MDR-ALERT"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "infra.jsonl")(_ :+ ghostInfra)
    run(b)("R-MDR-ALERT").verdict shouldBe Verdict.Violated

  test("a ghost alerted within the detection delay satisfies R-MDR-ALERT"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "infra.jsonl")(_ :+ ghostInfra)
    TestBundles.writeLines(b.dir, "mdr_alerts.jsonl", Vector("""{"alertId":"a","raisedAt":"2026-10-15T10:03:00Z","severity":"P1","taxonomy":"governance","detector":"ghosts","interactionIds":[],"summary":"model call without interaction id"}"""))
    run(b)("R-MDR-ALERT").verdict shouldBe Verdict.Satisfied

  test("a heartbeat gap is an ungoverned window"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "mdr_heartbeats.jsonl")(ls => ls.filterNot(l => l.contains("10:02:00Z") || l.contains("10:02:30Z") || l.contains("10:01:30Z")))
    val r = run(b)("R-MDR-LIVE")
    r.verdict shouldBe Verdict.Violated
    r.reason should include ("ungoverned")

  test("without an MDR config the liveness check cannot run"):
    run(TestBundles.valid(), None)("R-MDR-LIVE").verdict shouldBe Verdict.NotRun
