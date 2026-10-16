package clad.conformance

import clad.conformance.probes.GhostProbe
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class GhostProbeScoringSpec extends AnyFunSuite with Matchers:
  private val cfg = Some(ProbeConfig(None, Some(GhostConfig("http://x", matchToleranceSeconds = 5)), Some(MdrConfig(30, 120))))
  private def setup(alerted: Boolean) =
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "bundle.json")(ls => ls.map(l => { val j = ujson.read(l); j("probeWindow") = ujson.Obj("start" -> "2026-10-15T10:01:30Z", "end" -> "2026-10-15T10:04:00Z"); j.render() }))
    TestBundles.rewrite(b.dir, "infra.jsonl")(_ :+ """{"ts":"2026-10-15T10:02:01Z","host":"api.deepinfra.com","method":"POST","path":"/v1/openai/chat/completions","status":401,"interactionId":null}""")
    if alerted then TestBundles.writeLines(b.dir, "mdr_alerts.jsonl", Vector("""{"alertId":"a","raisedAt":"2026-10-15T10:03:00Z","severity":"P1","taxonomy":"governance","detector":"ghosts","interactionIds":[],"summary":"bypass"}"""))
    val results = Files.createTempDirectory("probe")
    Files.writeString(results.resolve("probe_ghost.json"), """{"sentAt":"2026-10-15T10:02:00Z","status":401}""")
    (BundleReader.read(b.dir).toOption.get, results)

  test("an injected ghost that was seen and alerted satisfies R-PROBE-GHOST"):
    val (b, r) = setup(alerted = true)
    GhostProbe.score(b, Some(r), cfg).verdict shouldBe Verdict.Satisfied

  test("an injected ghost without an alert violates R-PROBE-GHOST"):
    val (b, r) = setup(alerted = false)
    GhostProbe.score(b, Some(r), cfg).verdict shouldBe Verdict.Violated

  test("no probe results: NOT_RUN"):
    val (b, _) = setup(alerted = true)
    GhostProbe.score(b, None, cfg).verdict shouldBe Verdict.NotRun
