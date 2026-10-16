package clad.conformance

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EndToEndSpec extends AnyFunSuite with Matchers:
  test("a valid bundle scores every non-probe requirement SATISFIED or the documented NOT_RUNs"):
    val built = TestBundles.valid()
    val b = BundleReader.read(built.dir).toOption.get
    val rs = Conformance.evaluate(b, Some(ProbeConfig(None, None, Some(MdrConfig(30, 120)))), None)
    val byId = rs.map(r => r.id -> r.verdict).toMap
    val expectedNotRun = Set("R-AI1", "R-POSTURE", "R-PROBE-GHOST", "R-BREAKGLASS", "R-GSPS", "R-CS")
    rs.filterNot(r => expectedNotRun(r.id)).foreach(r => withClue(s"${r.id}: ${r.reason}")(r.verdict shouldBe Verdict.Satisfied))
    expectedNotRun.foreach(id => byId(id) shouldBe Verdict.NotRun)
    Scorecard.exitCode(rs) shouldBe 2
    val md = Scorecard.markdown(b, rs)
    md should include ("| R-EA3 |")
    md should include ("not implemented by the conformance module")
    val js = Scorecard.json(b, built.dir, rs)
    js("summary")("ghostRate").num shouldBe 0.0
    js("requirements").arr.size shouldBe rs.size

  test("every requirement id in the spec catalogue is produced exactly once"):
    val b = BundleReader.read(TestBundles.valid().dir).toOption.get
    val ids = Conformance.evaluate(b, None, None).map(_.id)
    ids.distinct.size shouldBe ids.size
    ids.toSet shouldBe Set("R-AI1", "R-AI2", "R-AI3", "R-AI4", "R-AI5", "R-AI6", "R-ANCHOR", "R-EA1", "R-EA2", "R-EA3",
      "R-EA4", "R-AX5", "R-CFG-PARSE", "R-CFG-MONO", "R-CFG-TENSION", "R-EPG-REEVAL", "R-EPG-BLOCK", "R-ROC-PRESENT",
      "R-ROC-DECISION", "R-ROC-THRESH", "R-POSTURE", "R-MDR-ALERT", "R-MDR-LIVE", "R-PROBE-GHOST",
      "R-BREAKGLASS", "R-GSPS", "R-CS")

  test("exit code 1 when anything is violated"):
    Scorecard.exitCode(Vector(RequirementResult("x", "x", "x", Verdict.Violated))) shouldBe 1
    Scorecard.exitCode(Vector(RequirementResult("x", "x", "x", Verdict.Satisfied))) shouldBe 0
