package clad.gateway.config

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path, Paths}

class GovernanceSpec extends AnyFunSuite:
  val fixture: Path = Paths.get(getClass.getResource("/governance/constraints.yaml").toURI)
  def real: Governance = Governance.load(fixture).fold(e => fail(e), identity)
  def mutated(f: ujson.Obj => Unit): Either[String, Governance] =
    val copy = ujson.read(ujson.write(YamlJson.read(fixture))) match
      case o: ujson.Obj => o
      case other        => fail(s"fixture is not an object: $other")
    f(copy)
    Governance.fromJson(copy)

  test("the agent path has 6 enterprise, 1 department and 1 project constraint") {
    val levels = real.path("agent").map(_.level)
    assert(levels.count(_ == "enterprise") == 6 && levels.count(_ == "department") == 1 && levels.count(_ == "project") == 1)
  }

  test("the exported config loads in clad, is deterministic, and has the path's mechanical checkers only") {
    val g = real
    assert(g.configJson("agent") == g.configJson("agent"))
    val cfg = clad.config.ConfigLoader.loadFromString(g.configJson("agent")).fold(e => fail(e.mkString), identity)
    assert(clad.config.ConfigLoader.buildHierarchy(cfg).isRight)
    assert(cfg.checkers.map(_.property).toSet == Set("contains_pii", "zdr_provider", "approved_model",
      "untrusted_data_rule", "security_recommendation_instruction", "grounding_instruction", "scenario_attribution"))
    assert(g.configDigest("agent").startsWith("sha256:"))
  }

  test("pending approval exports an empty approver") {
    val g = real
    assert(g.approvalPending.isDefined)
    val cfg = g.governanceConfig("agent")
    val all = cfg.constraints.enterprise ++ cfg.constraints.department ++ cfg.constraints.project
    assert(all.forall(c => c.approver == "" && c.author == "claude"))
  }

  test("a matching approval exports the approver; an edit afterwards puts the set back to pending") {
    val digest = real.approvalDigest
    def approve(o: ujson.Obj): Unit =
      o("approval") = ujson.Obj("status" -> "approved", "approved_by" -> "david", "approved_at" -> "2026-10-16",
                                "constraints_digest" -> digest)
    val approved = mutated(approve).fold(e => fail(e), identity)
    assert(approved.approvalPending.isEmpty)
    assert(approved.governanceConfig("agent").constraints.enterprise.forall(_.approver == "david"))
    val edited = mutated { o => approve(o); o("enterprise").arr(0).obj("version") = "2" }.fold(e => fail(e), identity)
    assert(edited.approvalPending.exists(_.contains("digest")))
  }

  test("bundle projects carry tier and expected components") {
    assert(real.bundleProjects("router") == ujson.Obj("tier" -> "Standard", "expectedComponents" -> ujson.Arr("roc")))
  }

  private def rejects(message: String)(f: ujson.Obj => Unit): Unit =
    val r = mutated(f)
    assert(r.left.exists(_.contains(message)), s"expected an error containing '$message', got $r")

  test("author equal to approver is refused")(rejects("author and approver")(o => o("enterprise").arr(0).obj("approver") = "claude"))
  test("a non-human approver is refused")(rejects("human")(o => o("enterprise").arr(0).obj("approver") = "cline"))
  test("a comma in a keyword is refused")(rejects(",")(o => o("checkers")("grounding_instruction").obj("keywords") = ujson.Arr("never, invent")))
  test("a missing checker is refused")(rejects("checker")(o => o("checkers").obj.remove("zdr_provider")))
  test("O and F of one property on a path is a contradiction")(rejects("contradiction") { o =>
    o("departments")("advice")("constraints").arr.append(ujson.Obj("property" -> "contains_pii", "type" -> "obligation",
      "domain" -> "security", "version" -> "1", "evaluability" -> "mechanical", "author" -> "claude", "approver" -> "david"))
  })
  test("a critical domain needs a central approver")(rejects("central") { o =>
    o("agents").arr.append(ujson.Obj("id" -> "eve", "name" -> "Eve", "kind" -> "human",
      "domains" -> ujson.Arr("legal"), "levels" -> ujson.Arr("enterprise")))
    o("enterprise").arr(1).obj("approver") = "eve"
  })
  test("approval by a non-central approver is refused")(rejects("approved_by") { o =>
    o("approval") = ujson.Obj("status" -> "approved", "approved_by" -> "claude", "approved_at" -> "2026-10-16",
                              "constraints_digest" -> "sha256:x")
  })

  test("a missing file is a Left, not an exception") {
    val r = Governance.load(Paths.get("/nonexistent/constraints.yaml"))
    assert(r.left.exists(_.contains("/nonexistent/constraints.yaml")), r)
  }
  test("malformed YAML is a Left, not an exception") {
    val f = Files.createTempFile("constraints", ".yaml")
    try
      Files.writeString(f, "agents: [unclosed\n  : : {")
      val r = Governance.load(f)
      assert(r.left.exists(_.startsWith(f.toString)), r)
    finally Files.delete(f)
  }
  test("an unknown tier is refused")(rejects("tier")(o => o("projects")("agent").obj("tier") = "critical"))
  test("an unknown checker type is refused")(rejects("must be one of regex, keyword, structural")(o =>
    o("checkers")("zdr_provider").obj("type") = "magic"))
  test("an unknown constraint type is refused")(rejects("must be one of obligation, prohibition")(o =>
    o("enterprise").arr(0).obj("type") = "forbid"))
  test("an unknown evaluability is refused")(rejects("must be one of mechanical, procedural")(o =>
    o("enterprise").arr(0).obj("evaluability") = "magic"))
  test("a project in two departments is refused")(rejects("exactly one department")(o =>
    o("departments")("planning")("projects").arr.append(ujson.Str("agent"))))
  test("an author not authorised for the domain and level is refused")(rejects("not authorised") { o =>
    o("agents").arr.append(ujson.Obj("id" -> "bob", "name" -> "Bob", "kind" -> "agent",
      "domains" -> ujson.Arr("business"), "levels" -> ujson.Arr("project")))
    o("enterprise").arr(0).obj("author") = "bob"
  })
