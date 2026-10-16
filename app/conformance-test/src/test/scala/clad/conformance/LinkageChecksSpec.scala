package clad.conformance

import clad.conformance.checks.LinkageChecks
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class LinkageChecksSpec extends AnyFunSuite with Matchers:
  private def run(b: TestBundles.Built) = LinkageChecks.all(BundleReader.read(b.dir).toOption.get).map(r => r.id -> r).toMap
  private val nullInfra = """{"ts":"2026-10-15T10:02:00Z","host":"api.deepinfra.com","method":"POST","path":"/x","status":200,"interactionId":null}"""

  test("valid bundle: no ghosts"):
    val r = run(TestBundles.valid())
    Seq("R-EA1", "R-EA2", "R-EA3").foreach(id => withClue(r(id).reason)(r(id).verdict shouldBe Verdict.Satisfied))
    r("R-EA3").metrics("ghostRate") shouldBe 0.0

  test("an infra call with no interaction id is a ghost"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "infra.jsonl")(_ :+ nullInfra)
    val r = run(b)
    r("R-EA1").verdict shouldBe Verdict.Violated
    r("R-EA3").verdict shouldBe Verdict.Violated
    r("R-EA3").metrics("ghostCount") shouldBe 1.0
    r("R-EA3").metrics("ghostRate") shouldBe 0.5

  test("baseline: no GIL but infra present is VIOLATED with every call a ghost"):
    val b = TestBundles.valid()
    Files.delete(b.dir.resolve("gil.jsonl"))
    TestBundles.rewrite(b.dir, "infra.jsonl")(_ :+ nullInfra)
    val r = run(b)
    r("R-EA1").verdict shouldBe Verdict.Violated
    r("R-EA3").verdict shouldBe Verdict.Violated
    r("R-EA3").metrics("ghostCount") shouldBe 2.0
    r("R-EA3").metrics("ghostRate") shouldBe 1.0
    r("R-EA2").verdict shouldBe Verdict.NotRun

  test("a GIL entry with no audit record is a ghost"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "gil.jsonl")(_ :+ """{"interactionId":"0192f0c0-0000-7000-8000-000000000002","registeredAt":"2026-10-15T10:03:00Z","metadata":{"project":"agent","principal":"api:test","tier":"Critical"}}""")
    run(b)("R-EA3").verdict shouldBe Verdict.Violated

  test("a GIL entry without a principal violates R-EA2"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "gil.jsonl")(ls => ls.map(_.replace("\"principal\":\"api:test\",", "")))
    run(b)("R-EA2").verdict shouldBe Verdict.Violated

  test("ghosts inside the probe window do not count against the workload"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "bundle.json")(ls => ls.map(l => { val j = ujson.read(l); j("probeWindow") = ujson.Obj("start" -> "2026-10-15T10:01:30Z", "end" -> "2026-10-15T10:02:30Z"); j.render() }))
    TestBundles.rewrite(b.dir, "infra.jsonl")(_ :+ nullInfra)
    run(b)("R-EA3").verdict shouldBe Verdict.Satisfied

  test("no infra file: R-EA1 NOT_RUN"):
    val b = TestBundles.valid()
    Files.delete(b.dir.resolve("infra.jsonl"))
    run(b)("R-EA1").verdict shouldBe Verdict.NotRun
