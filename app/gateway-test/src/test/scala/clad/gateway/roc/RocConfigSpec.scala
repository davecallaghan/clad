package clad.gateway.roc

import clad.config.ConfigLoader
import clad.gateway.config.*
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.jdk.CollectionConverters.*

class RocConfigSpec extends AnyFunSuite:
  private val gov: Governance = Governance.load(Fixtures.dir.resolve("constraints.yaml")).fold(e => fail(e), identity)
  private val roc: RocConfig = RocConfig.load(Fixtures.dir, gov).fold(e => fail(e), identity)

  private def template(id: String, project: String) =
    Template(id, "1", project, "Standard", "m", "t", "x", "text", "none", Map.empty)

  /** The fixture directory copied to a temp directory with roc.yaml rewritten by `f`. */
  private def withRoc(f: String => String): Either[String, RocConfig] =
    val dir = Files.createTempDirectory("roc")
    val s = Files.walk(Fixtures.dir)
    try s.iterator().asScala.foreach { p =>
      val target = dir.resolve(Fixtures.dir.relativize(p).toString)
      if Files.isDirectory(p) then Files.createDirectories(target) else Files.copy(p, target)
    }
    finally s.close()
    val file = dir.resolve("roc.yaml")
    Files.writeString(file, f(Files.readString(file)))
    RocConfig.load(dir, gov)

  test("the fixture loads, with the classifier profile") {
    assert(roc.constraints.size == 9 && roc.thresholds.size == 2 && roc.routerIntents.contains("plan_edit"))
    assert(roc.classifier.id == "llama_guard" && roc.classifier.verdictTokens > 0 && roc.classifier.measuredFp.isEmpty)
    assert(roc.safeResponses.contains("default") && roc.retryVariant.nonEmpty)
  }

  test("forTemplate picks constraints by template and project; router.jev gets route_valid and the global deterministic ones") {
    assert(roc.forTemplate(template("agent.answer", "agent")).map(_.property).toSet ==
      Set("numbers_grounded", "contains_pii", "guarantee_language", "specialized_advice", "unsafe_content"))
    assert(roc.forTemplate(template("router.jev", "router")).map(_.property).toSet ==
      Set("route_valid", "contains_pii", "guarantee_language"))
  }

  test("tau: the lowest applicable level wins, and a level that does not apply is ignored") {
    val lowered = roc.copy(thresholds = roc.thresholds ++ Vector(
      ThresholdDef("unsafe_content", "project", "agent", 0.3, "1", "david"),
      ThresholdDef("unsafe_content", "department", "planning", 0.4, "1", "david")))
    assert(lowered.tau("unsafe_content", "agent", gov) == 0.3)
    assert(lowered.tau("unsafe_content", "planner-chat", gov) == 0.4)
    assert(lowered.tau("unsafe_content", "assistant", gov) == 0.5)
  }

  test("a project threshold above the enterprise one is refused") {
    val r = withRoc(_.replace("thresholds:\n", "thresholds:\n  - {property: unsafe_content, level: project, scope: agent, tau: 0.6, version: \"1\"}\n")
      .replace("tau_max: 0.5", "tau_max: 0.9"))
    assert(r.left.exists(_.contains("a lower level may only lower")), r.toString)
  }

  test("a project threshold below the enterprise one loads") {
    val r = withRoc(_.replace("thresholds:\n", "thresholds:\n  - {property: unsafe_content, level: project, scope: agent, tau: 0.2, version: \"1\"}\n"))
    assert(r.isRight, r.toString)
  }

  test("a Critical threshold above tau_max is refused") {
    val r = withRoc(_.replace("tau_max: 0.5", "tau_max: 0.4"))
    assert(r.left.exists(_.contains("tau_max")), r.toString)
  }

  test("a classifier constraint without an enterprise threshold is refused") {
    val r = withRoc(_.linesIterator.filterNot(_.contains("unsafe_content, level: enterprise")).mkString("\n"))
    assert(r.left.exists(e => e.contains("unsafe_content") && e.contains("enterprise threshold")), r.toString)
  }

  test("a threshold scope that names nothing is refused") {
    val r = withRoc(_.replace("thresholds:\n", "thresholds:\n  - {property: unsafe_content, level: project, scope: nowhere, tau: 0.2, version: \"1\"}\n"))
    assert(r.left.exists(_.contains("unknown project")), r.toString)
  }

  test("a missing key is a Left naming the file") {
    val r = withRoc(_.replace("retry_variant:", "retry_variant_x:"))
    assert(r.left.exists(e => e.contains("roc.yaml") && e.contains("retry_variant")), r.toString)
  }

  test("governanceConfig exports the output constraints and thresholds, and clad's ConfigLoader accepts them") {
    val cfg = gov.governanceConfig("agent", roc)
    assert(cfg.outputConstraints.map(_.property).toSet ==
      Set("numbers_grounded", "schema_valid", "contains_pii", "guarantee_language", "specialized_advice", "unsafe_content"))
    assert(cfg.outputConstraints.forall(_.riskTier == "Critical"))
    assert(cfg.outputConstraints.find(_.property == "specialized_advice").exists(_.config == Map("kind" -> "classifier", "category" -> "S6")))
    assert(cfg.thresholds.map(t => t.property -> t.flagThreshold).toSet ==
      Set("specialized_advice" -> 0.5, "unsafe_content" -> 0.5, "tau_max" -> 0.5))
    assert(cfg.thresholds.exists(t => t.property == "tau_max" && t.riskTier == "Critical" && t.level == "enterprise"))
    assert(ConfigLoader.loadFromString(gov.configJson("agent", roc)).flatMap(ConfigLoader.buildHierarchy).isRight)
    // the base fixture has no router.jev prompt, so route_valid (which names that template) is not exported for router
    assert(gov.governanceConfig("router", roc).outputConstraints.map(_.property).toSet == Set("contains_pii", "guarantee_language"))
  }

  test("a conformance RocChecks run over a bundle built from that config gives R-ROC-THRESH satisfied") {
    val dir = Files.createTempDirectory("bundle")
    val json = gov.configJson("agent", roc)
    Files.createDirectories(dir.resolve("configs"))
    Files.writeString(dir.resolve("configs").resolve(Canonical.sha256(json).stripPrefix("sha256:") + ".json"), json)
    Files.writeString(dir.resolve("bundle.json"), ujson.Obj("bundleVersion" -> 1, "app" -> "test",
      "window" -> ujson.Obj("start" -> Instant.EPOCH.toString, "end" -> Instant.EPOCH.plusSeconds(1).toString),
      "projects" -> gov.bundleProjects, "keys" -> ujson.Obj()).render())
    val bundle = clad.conformance.BundleReader.read(dir).fold(e => fail(e), identity)
    val r = clad.conformance.checks.RocChecks.all(bundle).find(_.id == "R-ROC-THRESH").getOrElse(fail("no R-ROC-THRESH"))
    assert(r.verdict == clad.conformance.Verdict.Satisfied, r.toString)
  }

  test("changing a tau in roc.yaml changes the approval digest and puts an approval back to pending") {
    def approvedBy(g: Governance, r: RocConfig): Governance =
      val o = ujson.read(ujson.write(g.raw)).obj
      o("approval") = ujson.Obj("status" -> "approved", "approved_by" -> "david", "approved_at" -> "2026-10-16",
                                "constraints_digest" -> g.approvalDigest(r))
      Governance.fromJson(o).fold(e => fail(e), identity)
    val approved = approvedBy(gov, roc)
    assert(approved.approvalPending(roc).isEmpty)
    val lowered = withRoc(_.replace("unsafe_content, level: enterprise, tau: 0.5", "unsafe_content, level: enterprise, tau: 0.4"))
      .fold(e => fail(e), identity)
    assert(approved.approvalDigest(lowered) != approved.approvalDigest(roc))
    assert(approved.approvalPending(lowered).exists(_.contains("digest")))
  }
