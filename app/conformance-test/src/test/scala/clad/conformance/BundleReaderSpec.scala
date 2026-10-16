package clad.conformance

import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class BundleReaderSpec extends AnyFunSuite with Matchers:
  test("reads every part of a valid bundle"):
    val b = BundleReader.read(TestBundles.valid().dir).toOption.get
    b.meta.projects("agent").expectedComponents shouldBe Vector("epg", "roc")
    b.gil.get.size shouldBe 1
    b.infra.get.head.interactionId shouldBe Some("0192f0c0-0000-7000-8000-000000000001")
    b.audit.get.size shouldBe 2
    b.configs(TestBundles.configDigest).computedDigest shouldBe TestBundles.configDigest
    b.artifacts(TestBundles.promptDigest).metadata("template_id") shouldBe "agent.answer"
    b.publicKeys.keySet shouldBe Set("comp1", "sup1")
    b.rawLines("audit.jsonl").size shouldBe 2

  test("a missing file is None, an empty file is Some(empty)"):
    val built = TestBundles.valid()
    Files.delete(built.dir.resolve("gil.jsonl"))
    val b = BundleReader.read(built.dir).toOption.get
    b.gil shouldBe None
    b.degraded shouldBe Some(Vector.empty)

  test("a missing bundle.json is an error"):
    val built = TestBundles.valid()
    Files.delete(built.dir.resolve("bundle.json"))
    BundleReader.read(built.dir).isLeft shouldBe true

  test("an undecodable audit line is an error naming the file and line"):
    val built = TestBundles.valid()
    TestBundles.rewrite(built.dir, "audit.jsonl")(_ :+ "{not json")
    BundleReader.read(built.dir).left.toOption.get should include ("audit.jsonl:3")

  test("infra lines with a null interaction id read as None"):
    val built = TestBundles.valid()
    TestBundles.rewrite(built.dir, "infra.jsonl")(_ :+ """{"ts":"2026-10-15T10:01:00Z","host":"api.deepinfra.com","method":"POST","path":"/x","status":401,"interactionId":null}""")
    BundleReader.read(built.dir).toOption.get.infra.get.last.interactionId shouldBe None
