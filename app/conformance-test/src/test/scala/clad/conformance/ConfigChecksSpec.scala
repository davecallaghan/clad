package clad.conformance

import clad.conformance.checks.ConfigChecks
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ConfigChecksSpec extends AnyFunSuite with Matchers:
  private def run(b: TestBundles.Built) = ConfigChecks.all(BundleReader.read(b.dir).toOption.get).map(r => r.id -> r).toMap

  /** Replace the config file with edited JSON (and fix the file name and the records' configDigest
    * only where a test says so). */
  private def editConfig(b: TestBundles.Built)(f: String => String): String =
    val oldPath = b.dir.resolve(s"configs/${TestBundles.configDigest}.json")
    val json = f(Files.readString(oldPath))
    Files.delete(oldPath)
    val d = TestBundles.sha(json)
    Files.writeString(b.dir.resolve(s"configs/$d.json"), json)
    d

  /** Overwrite the config the records cite, keeping its file name: the edited text is what governed the window. */
  private def editCited(b: TestBundles.Built)(f: String => String): Unit =
    val path = b.dir.resolve(s"configs/${TestBundles.configDigest}.json")
    Files.writeString(path, f(Files.readString(path)))

  private val sameAuthorApprover = (s: String) => s.replaceFirst("\"approver\": \"david\"", "\"approver\": \"cline\"")

  test("valid bundle satisfies config requirements"):
    val r = run(TestBundles.valid())
    Seq("R-CFG-PARSE", "R-CFG-MONO", "R-CFG-TENSION", "R-EA4", "R-AX5").foreach(id => withClue(id + " " + r(id).reason)(r(id).verdict shouldBe Verdict.Satisfied))

  test("author equal to approver violates R-EA4"):
    val b = TestBundles.valid()
    editCited(b)(sameAuthorApprover)
    run(b)("R-EA4").verdict shouldBe Verdict.Violated

  test("R-EA4 checks only the configs that governed records in the window; an older version is history"):
    val b = TestBundles.valid()
    val old = sameAuthorApprover(Files.readString(b.dir.resolve(s"configs/${TestBundles.configDigest}.json")))
    Files.writeString(b.dir.resolve(s"configs/${TestBundles.sha(old)}.json"), old)
    val r = run(b)("R-EA4")
    r.verdict shouldBe Verdict.Satisfied
    r.evidence.mkString should include("1 of 2 config versions")

  test("with no records in the window, R-EA4 checks every config version"):
    val b = TestBundles.valid()
    TestBundles.writeLines(b.dir, "audit.jsonl", Vector.empty)
    val old = sameAuthorApprover(Files.readString(b.dir.resolve(s"configs/${TestBundles.configDigest}.json")))
    Files.writeString(b.dir.resolve(s"configs/${TestBundles.sha(old)}.json"), old)
    run(b)("R-EA4").verdict shouldBe Verdict.Violated

  test("an approver who is not human violates R-EA4"):
    val b = TestBundles.valid()
    editCited(b)(_.replace("\"author\": \"cline\", \"approver\": \"david\"", "\"author\": \"david\", \"approver\": \"cline\""))
    run(b)("R-EA4").verdict shouldBe Verdict.Violated

  test("a contradiction violates R-CFG-MONO"):
    val b = TestBundles.valid()
    editConfig(b)(_.replace("\"department\": []", """"department": [{"property": "contains_pii", "constraintType": "obligation", "level": "department", "domain": "security", "version": "1", "evaluability": "mechanical", "author": "cline", "approver": "david"}]"""))
    run(b)("R-CFG-MONO").verdict shouldBe Verdict.Violated

  test("a config file whose bytes do not match the digest records cite violates R-AX5"):
    val b = TestBundles.valid()
    editConfig(b)(_.replace("\"version\": \"1\",", "\"version\": \"2\","))
    run(b)("R-AX5").verdict shouldBe Verdict.Violated

  test("an artifact whose content does not hash to its name violates R-AX5"):
    val b = TestBundles.valid()
    val p = b.dir.resolve(s"artifacts/${TestBundles.promptDigest}.json")
    Files.writeString(p, Files.readString(p).replace("You answer", "You guess"))
    run(b)("R-AX5").verdict shouldBe Verdict.Violated

  test("no configs: NOT_RUN"):
    val b = TestBundles.valid()
    Files.delete(b.dir.resolve(s"configs/${TestBundles.configDigest}.json"))
    run(b)("R-CFG-PARSE").verdict shouldBe Verdict.NotRun
