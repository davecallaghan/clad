package clad.conformance

import clad.audit.*
import clad.conformance.checks.ChainChecks
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ChainChecksSpec extends AnyFunSuite with Matchers:
  private def run(built: TestBundles.Built) =
    ChainChecks.all(BundleReader.read(built.dir).toOption.get).map(r => r.id -> r).toMap

  test("a valid bundle satisfies every chain requirement"):
    val r = run(TestBundles.valid())
    Seq("R-AI2", "R-AI3", "R-AI4", "R-AI5", "R-ANCHOR").foreach(id => withClue(id + r(id).reason)(r(id).verdict shouldBe Verdict.Satisfied))

  test("a broken link violates R-AI2"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "audit.jsonl")(ls => ls.reverse)
    run(b)("R-AI2").verdict shouldBe Verdict.Violated

  test("a flipped byte in a record violates R-AI4 (recorded digest differs)"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "audit.jsonl")(ls => ls.updated(0, ls(0).replace("\"outcome\":\"pass\"", "\"outcome\":\"block\"")))
    val r = run(b)
    r("R-AI4").verdict shouldBe Verdict.Violated
    r("R-AI3").verdict shouldBe Verdict.Violated

  test("a record without recordedDigest is unverifiable, so R-AI4 is violated"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "audit.jsonl")(ls => ls.map(l => ujson.read(l) match { case o: ujson.Obj => o.value.remove("recordedDigest"); o.render(); case x => x.render() }))
    run(b)("R-AI4").reason should include ("unverifiable")

  test("signature by another key violates R-AI3"):
    val b = TestBundles.valid()
    val (other, otherPub) = Ed25519KeyManagement.generate("comp1")
    java.nio.file.Files.writeString(b.dir.resolve("keys/comp1.pub.pem"), otherPub)
    run(b)("R-AI3").verdict shouldBe Verdict.Violated

  test("a missing ROC record violates R-AI5"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "audit.jsonl")(ls => ls.filterNot(_.contains("\"component\":\"roc\"")))
    run(b)("R-AI5").verdict shouldBe Verdict.Violated

  test("an EPG block makes the interaction complete without a ROC record"):
    val b = TestBundles.valid()
    val id = "0192f0c0-0000-7000-8000-000000000001"
    val recs = TestBundles.chain(b.components, Vector(TestBundles.epgRecord(id, TestBundles.at(10), satisfied = false, "block")))
    TestBundles.writeLines(b.dir, "audit.jsonl", recs.map(AuditRecordCodec.encode))
    TestBundles.writeLines(b.dir, "anchors.jsonl", Vector(ujson.Obj("ts" -> TestBundles.at(13).toString, "chainLength" -> 1, "headDigest" -> recs.last.record.digest).render()))
    run(b)("R-AI5").verdict shouldBe Verdict.Satisfied

  test("a truncated tail violates R-ANCHOR"):
    val b = TestBundles.valid()
    TestBundles.rewrite(b.dir, "audit.jsonl")(ls => ls.init)
    run(b)("R-ANCHOR").verdict shouldBe Verdict.Violated

  test("no audit file means NOT_RUN, never SATISFIED"):
    val b = TestBundles.valid()
    java.nio.file.Files.delete(b.dir.resolve("audit.jsonl"))
    val r = run(b)
    Seq("R-AI2", "R-AI3", "R-AI4").foreach(id => r(id).verdict shouldBe Verdict.NotRun)
