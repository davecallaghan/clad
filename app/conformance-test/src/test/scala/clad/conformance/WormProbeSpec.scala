package clad.conformance

import clad.conformance.probes.WormProbe
import org.scalatest.funsuite.AnyFunSuite

class WormProbeSpec extends AnyFunSuite:
  test("an unreadable password file is NOT_RUN with the reason, never a crash of the whole scorecard") {
    val r = WormProbe.run(WormConfig("jdbc:postgresql://127.0.0.1:1/x", "u", "/nonexistent/writer.pw", Vector("governance.gil")))
    assert(r.id == "R-AI1" && r.verdict == Verdict.NotRun)
    assert(r.reason.contains("password file") && r.reason.contains("/nonexistent/writer.pw"))
  }
