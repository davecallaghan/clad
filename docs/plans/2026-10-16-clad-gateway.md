# clad-gateway Implementation Plan (gateway core, EPG, export, personal_finance switch)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `gateway` sbt module to clad: an OpenAI-compatible governed proxy (GIL, EPG, upstream with interaction id, supervisor postures, containment, signed hash chain in Postgres) with a bundle exporter, then switch personal_finance to it and delete its Python enforcement core.

**Architecture:** One new module `app/gateway` (package `clad.gateway`) built on clad's existing types (`AuditRecord`, `SignedAuditRecord`, `Ed25519KeyManagement`, `ChainAnchor`, `GilEntry`, `PromptEvaluator`, `ConfigLoader`, `SignedDegradedRecord`). It reads the adopter's YAML configuration, stores evidence in an append-only Postgres schema, serves HTTP with http4s (as `api` does), and exports the conformance bundle that `clad conformance` already reads. personal_finance keeps its configuration and call sites and points them at the gateway.

**Tech Stack:** Scala 3.3.8, sbt 1.10.7, http4s 0.23.30 (ember server), cats-effect 3.5.7, upickle 4.1.0, SnakeYAML 2.3, PostgreSQL JDBC 42.7.4, scalatest 3.2.19, JDK 17 (`java.net.http`, Ed25519), sbt-assembly 2.3.0. personal_finance side: Python 3.12 (uv), httpx, respx; TypeScript (vitest).

**Spec:** `docs/specs/2026-10-16-clad-gateway-design.md` (clad repo). Read it first. Companion: `docs/specs/2026-10-15-conformance-design.md` (bundle format, R-* ids).

**Scope:** spec sections 1–9, 11 (Python and TypeScript client changes inside personal_finance), 12 and 13. **Not in this plan:** ROC (spec 7 step 7; needs the Llama Guard spike, spec 14.1) and the MDR worker (spec 10). In this plan the pipeline has no ROC stage: a delivered call's outcome is `pass`. ROC and MDR get their own plans.

## Global Constraints

- clad work: worktree `/Users/david/workspace/clad-conformance`, branch `feature/conformance`. Never touch `/Users/david/workspace/clad` (another branch with uncommitted work). sbt commands run from `/Users/david/workspace/clad-conformance/app`.
- personal_finance work (Tasks 12–14): worktree `/Users/david/workspace/personal_finance-clad-enforcement`, branch `feature/clad-enforcement`. The live pod runs from that worktree; Tasks 12–14 mark the steps that touch it **OPERATOR**.
- **Do not merge, push or open a PR** in either repo.
- clad rules (its `CLAUDE.md` gates): no literal limits, timeouts, retry counts or tolerances in main code. Every such number comes from the adopter's `runtime.yaml`. No `.head`, `.take(n)`, `.slice`, `.limit` or SQL `LIMIT n` with literals in main code (use `headOption`, pattern matching, `max(seq)`). Test fixture files must not contain limit-named keys (`max_*`, `*_limit`, `*timeout*`, `*retries*`): tests build `Runtime` values in Scala, not from a fixture file.
- No swallowed exceptions: every `catch` returns a `Left`/`Failure` carrying the message, or rethrows.
- The audit record format is clad's: build `AuditRecord` and sign with `SignedAuditRecord.sign`; encode with `AuditRecordCodec.encode` / `DegradedRecordCodec.encode`. Never hand-write the canonical string.
- Integration tests need a Postgres server: `CLAD_TEST_PG_ADMIN_URL`, a JDBC URL for a superuser (for the user's Homebrew postgresql@16: `jdbc:postgresql://localhost:5432/postgres?user=david`). Tests create and drop their own databases, whose names start `clad_gw_test_`. If the variable is unset, the suite **fails** with a message naming it (a cancelled test is not a passing test).
- Commit messages end with the implementer's `Co-Authored-By:` line.
- Before each clad commit: `cicd/llm/preflight.sh <changed files>`. Every gate must be SATISFIED or not applicable.

## Review Focus

- **Concurrent calls.** Two requests at the same moment must leave one linear chain: no two records may claim the same predecessor (Task 5, concurrency test).
- **Slot values that look like slots or regex replacements.** A var containing `{{other}}` or `$1` must be inserted literally, never re-expanded (Task 2, render tests).
- **A provider reply that is 200 but not a chat completion** (no `choices`, empty list, HTML). It must be treated as upstream unavailable, with a degraded record, never as a 500 or a delivered `null` (Task 7 and Task 8 tests).
- **A principal outside the token's prefix**, or an email with different case. The first is a 400 with nothing registered; the second gives the same pseudonym (Task 7 and Task 9 tests).
- **Gateway restart.** After a restart the next record must link to the stored chain head, not start a new chain (Task 5, a test with a second store instance).

---

### Task 1: Module skeleton, YAML loading and the constraint hierarchy

**Files:**
- Modify: `app/build.sbt` (add `gateway`, `gateway-test`; add both to `root.aggregate`)
- Create: `app/gateway/src/main/scala/clad/gateway/config/YamlJson.scala`, `app/gateway/src/main/scala/clad/gateway/config/Canonical.scala`, `app/gateway/src/main/scala/clad/gateway/config/Governance.scala`
- Create: `app/gateway-test/src/test/resources/governance/constraints.yaml` (copy of personal_finance `config/governance/constraints.yaml` with `name: personal_finance` added as the first key)
- Test: `app/gateway-test/src/test/scala/clad/gateway/config/GovernanceSpec.scala`

**Interfaces:**
- Produces:

```scala
object YamlJson { def read(path: java.nio.file.Path): ujson.Value; def parse(text: String): ujson.Value }
object Canonical { def render(v: ujson.Value): String; def sha256(s: String): String }   // "sha256:" + hex
final case class AgentDef(id: String, name: String, kind: String, domains: Vector[String], levels: Vector[String])
final case class ConstraintDef(property: String, ob: Boolean, level: String, domain: String, version: String,
                               evaluability: String, author: String, approver: String)   // level: enterprise|department|project
final case class CheckerDef(property: String, kind: String, patterns: Vector[String], keywords: Vector[String],
                            threshold: Int, key: String, value: String)
final case class ProjectDef(name: String, department: String, tier: String, expectedComponents: Vector[String])
final class Governance:
  val name: String; val version: String; val agents: Vector[AgentDef]; val projects: Map[String, ProjectDef]
  val checkers: Map[String, CheckerDef]
  def humans: Set[String]
  def path(project: String): Vector[ConstraintDef]          // enterprise ++ department ++ project
  def approvalDigest: String
  def approvalPending: Option[String]                        // None when approved for the current digest
  def governanceConfig(project: String): clad.config.GovernanceConfig
  def configJson(project: String): String                    // ConfigWriter.toPrettyJson(governanceConfig(project))
  def configDigest(project: String): String                  // Canonical.sha256(configJson(project))
  def bundleProjects: ujson.Obj                              // {"agent": {"tier", "expectedComponents"}, ...}
object Governance { def fromJson(raw: ujson.Value): Either[String, Governance]; def load(path: java.nio.file.Path): Either[String, Governance] }
```

- [ ] **Step 1: build.sbt**

Add after the `conformance-test` project, and add `gateway, \`gateway-test\`` to `root`'s `aggregate`:

```scala
lazy val gateway = project
  .in(file("gateway"))
  .dependsOn(core, evaluation, runtime, audit, output, integrity, config, api)
  .settings(
    commonSettings,
    name := "clad-gateway",
    libraryDependencies ++= Seq(
      "org.yaml"       % "snakeyaml"           % "2.3",
      "org.postgresql" % "postgresql"          % "42.7.4",
      "org.http4s"    %% "http4s-ember-server" % http4sVersion,
      "org.http4s"    %% "http4s-dsl"          % http4sVersion,
      "org.typelevel" %% "cats-effect"         % catsEffectVersion
    ),
    Compile / mainClass := Some("clad.gateway.Main")
  )

lazy val `gateway-test` = project
  .in(file("gateway-test"))
  .dependsOn(gateway, conformance, `runtime-test`, `audit-test`)
  .settings(
    commonSettings,
    name := "clad-gateway-test",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"            % "3.2.19",
      "org.http4s"     %% "http4s-ember-client"  % http4sVersion  % Test,
      "org.typelevel"  %% "cats-effect-testing-scalatest" % "1.6.0" % Test
    )
  )
```

- [ ] **Step 2: Fixture.** Copy `/Users/david/workspace/personal_finance-clad-enforcement/config/governance/constraints.yaml` to `app/gateway-test/src/test/resources/governance/constraints.yaml`, then insert `name: personal_finance` as the first line.

- [ ] **Step 3: Failing tests** (`GovernanceSpec.scala`):

```scala
package clad.gateway.config

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path, Paths}

class GovernanceSpec extends AnyFunSuite:
  val fixture: Path = Paths.get(getClass.getResource("/governance/constraints.yaml").toURI)
  def real: Governance = Governance.load(fixture).fold(e => fail(e), identity)
  def mutated(f: ujson.Obj => Unit): Either[String, Governance] =
    val raw = YamlJson.read(fixture).obj
    val copy = ujson.read(ujson.write(raw)).obj
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
```

Run: `sbt "gateway-test/testOnly clad.gateway.config.GovernanceSpec"` — Expected: compilation errors (classes missing).

- [ ] **Step 4: Implement.**

`YamlJson.scala`:

```scala
package clad.gateway.config

import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** YAML (safe constructor only) as ujson, so every config file is handled as JSON afterwards. */
object YamlJson:
  def read(path: Path): ujson.Value = parse(Files.readString(path))

  def parse(text: String): ujson.Value = toJson(Yaml(SafeConstructor(LoaderOptions())).load[Object](text))

  private def toJson(v: Any): ujson.Value = v match
    case null                   => ujson.Null
    case m: java.util.Map[?, ?] => ujson.Obj.from(m.asScala.toSeq.map((k, x) => k.toString -> toJson(x)))
    case l: java.util.List[?]   => ujson.Arr.from(l.asScala.map(toJson))
    case s: String              => ujson.Str(s)
    case b: java.lang.Boolean   => ujson.Bool(b)
    case n: java.lang.Number    => ujson.Num(n.doubleValue)
    case d: java.util.Date      => ujson.Str(d.toInstant.toString.takeWhile(_ != 'T'))
    case other                  => ujson.Str(other.toString)
```

`Canonical.scala`:

```scala
package clad.gateway.config

import java.security.MessageDigest

object Canonical:
  /** Compact JSON with every object's keys sorted: the input to approval digests. */
  def render(v: ujson.Value): String = sorted(v).render()

  private def sorted(v: ujson.Value): ujson.Value = v match
    case o: ujson.Obj => ujson.Obj.from(o.value.toSeq.sortBy(_._1).map((k, x) => k -> sorted(x)))
    case a: ujson.Arr => ujson.Arr.from(a.value.map(sorted))
    case other        => other

  def sha256(s: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8")).map("%02x".format(_)).mkString
```

`Governance.scala`:

```scala
package clad.gateway.config

import clad.config.*
import java.nio.file.Path

final case class AgentDef(id: String, name: String, kind: String, domains: Vector[String], levels: Vector[String])
final case class ConstraintDef(property: String, ob: Boolean, level: String, domain: String, version: String,
                               evaluability: String, author: String, approver: String)
final case class CheckerDef(property: String, kind: String, patterns: Vector[String], keywords: Vector[String],
                            threshold: Int, key: String, value: String)
final case class ProjectDef(name: String, department: String, tier: String, expectedComponents: Vector[String])

/** The adopter's constraints.yaml (spec section 5): hierarchy, authorship, checkers and the approval block. */
final class Governance private (
  val raw: ujson.Obj, val name: String, val version: String, val agents: Vector[AgentDef],
  val centralApprovers: Vector[String], val criticalDomains: Vector[String], val checkers: Map[String, CheckerDef],
  val enterprise: Vector[ConstraintDef], val departments: Map[String, Vector[ConstraintDef]],
  val projectConstraints: Map[String, Vector[ConstraintDef]], val projects: Map[String, ProjectDef]
):
  def humans: Set[String] = agents.filter(_.kind == "human").map(_.id).toSet

  def path(project: String): Vector[ConstraintDef] =
    enterprise ++ departments(projects(project).department) ++ projectConstraints(project)

  def approvalDigest: String =
    Canonical.sha256(Canonical.render(ujson.Obj.from(raw.value.toSeq.filterNot(_._1 == "approval"))))

  private def approval: Map[String, ujson.Value] = raw.value.get("approval") match
    case Some(o: ujson.Obj) => o.value.toMap
    case _                  => Map.empty

  private def str(m: Map[String, ujson.Value], k: String): Option[String] = m.get(k).collect { case ujson.Str(s) => s }

  def approvalPending: Option[String] =
    val a = approval
    if !str(a, "status").contains("approved") then Some(s"approval status is ${str(a, "status").getOrElse("absent")}")
    else if !str(a, "constraints_digest").contains(approvalDigest) then
      Some("approval constraints_digest does not match the current constraints")
    else if str(a, "approved_at").isEmpty then Some("approval has no approved_at")
    else None

  private def exportedApprover(c: ConstraintDef): String =
    if approvalPending.isEmpty && str(approval, "approved_by").contains(c.approver) then c.approver else ""

  private def cc(c: ConstraintDef): ConstraintConfig =
    ConstraintConfig(c.property, if c.ob then "obligation" else "prohibition", c.level, c.domain, c.version,
      c.evaluability, c.author, exportedApprover(c))

  private def binding(ch: CheckerDef): CheckerBinding = ch.kind match
    case "regex"   => CheckerBinding(ch.property, "regex", Map("patterns" -> ch.patterns.mkString(","), "mode" -> "any"))
    case "keyword" => CheckerBinding(ch.property, "keyword", Map("keywords" -> ch.keywords.mkString(","),
                                                               "threshold" -> ch.threshold.toString))
    case _         => CheckerBinding(ch.property, "structural", Map("key" -> ch.key, "value" -> ch.value))

  def governanceConfig(project: String): GovernanceConfig =
    val p = projects(project)
    val mechanical = path(project).filter(_.evaluability == "mechanical").map(_.property).distinct.sorted
    val posture = Governance.Posture(p.tier)
    GovernanceConfig(
      name = name, version = version, project = project,
      agents = agents.map(a => AgentConfig(a.id, a.name, a.domains, a.levels, a.kind)),
      constraints = ConstraintHierarchyConfig(enterprise.map(cc), departments(p.department).map(cc),
                                              projectConstraints(project).map(cc)),
      checkers = mechanical.map(m => binding(checkers(m))),
      failurePosture = FailurePostureConfig(posture, posture, "fail-closed"),
      centralApprovers = centralApprovers, criticalDomains = criticalDomains)

  def configJson(project: String): String = ConfigWriter.toPrettyJson(governanceConfig(project))
  def configDigest(project: String): String = Canonical.sha256(configJson(project))

  def bundleProjects: ujson.Obj = ujson.Obj.from(projects.toSeq.sortBy(_._1).map((n, p) =>
    n -> ujson.Obj("tier" -> p.tier, "expectedComponents" -> ujson.Arr.from(p.expectedComponents.map(ujson.Str(_))))))

object Governance:
  val Posture: Map[String, String] = Map("Critical" -> "fail-closed", "Standard" -> "fail-open-flagged", "Low" -> "fail-open")

  def load(path: Path): Either[String, Governance] = fromJson(YamlJson.read(path))

  private def strs(v: ujson.Value): Vector[String] = v.arr.map(_.str).toVector

  private def constraint(v: ujson.Value, level: String): ConstraintDef =
    val o = v.obj
    def s(k: String): String = o(k) match { case ujson.Str(x) => x; case other => other.render() }
    ConstraintDef(s("property"), s("type") == "obligation", level, s("domain"), s("version"), s("evaluability"),
      s("author"), s("approver"))

  def fromJson(raw: ujson.Value): Either[String, Governance] =
    try
      val o = raw.obj
      val agents = o("agents").arr.map { a =>
        AgentDef(a("id").str, a("name").str, a("kind").str, strs(a("domains")), strs(a("levels"))) }.toVector
      val checkers = o("checkers").obj.map { (p, c) =>
        val m = c.obj
        p -> CheckerDef(p, m("type").str, m.get("patterns").map(strs).getOrElse(Vector.empty),
          m.get("keywords").map(strs).getOrElse(Vector.empty), m.get("threshold").map(_.num.toInt).getOrElse(1),
          m.get("key").map(_.str).getOrElse(""), m.get("value").map(_.str).getOrElse(""))
      }.toMap
      val departments = o("departments").obj.map((d, v) => d -> v("constraints").arr.map(constraint(_, "department")).toVector).toMap
      val deptOf = o("departments").obj.toSeq.flatMap((d, v) => strs(v("projects")).map(_ -> d))
      val projects = o("projects").obj.map { (p, v) =>
        p -> ProjectDef(p, deptOf.find(_._1 == p).map(_._2).getOrElse(""), v("tier").str, strs(v("expected_components")))
      }.toMap
      val projectConstraints = o("projects").obj.map((p, v) => p -> v("constraints").arr.map(constraint(_, "project")).toVector).toMap
      val g = Governance(o, o.get("name").map(_.str).getOrElse("clad-gateway"), o("version") match {
          case ujson.Str(s) => s; case other => other.render() },
        agents, strs(o("central_approvers")), strs(o("critical_domains")), checkers,
        o("enterprise").arr.map(constraint(_, "enterprise")).toVector, departments, projectConstraints, projects)
      validate(g, deptOf.map(_._1)).toLeft(g)
    catch
      case e: NoSuchElementException => Left(s"constraints.yaml: missing field ${e.getMessage}")
      case e: ujson.Value.InvalidData => Left(s"constraints.yaml: ${e.getMessage}")

  private def validate(g: Governance, listedProjects: Seq[String]): Option[String] =
    val all = g.enterprise ++ g.departments.values.flatten ++ g.projectConstraints.values.flatten
    val agent = g.agents.map(a => a.id -> a).toMap
    val perConstraint = all.iterator.flatMap { c =>
      Iterator(
        Option.when(c.evaluability == "mechanical" && !g.checkers.contains(c.property))(s"${c.property}: mechanical constraint has no checker"),
        Option.when(c.author == c.approver)(s"${c.property}: author and approver must differ (dual control)"),
        Option.when(!agent.get(c.approver).exists(_.kind == "human"))(s"${c.property}: approver ${c.approver} must be a declared human"),
        Option.when(!agent.get(c.author).exists(a => a.domains.contains(c.domain) && a.levels.contains(c.level)))(
          s"${c.property}: author ${c.author} is not authorised for ${c.domain} at ${c.level}"),
        Option.when(g.criticalDomains.contains(c.domain) && !g.centralApprovers.contains(c.approver))(
          s"${c.property}: ${c.domain} is critical, so the approver must be a central approver")
      ).flatten
    }
    val commas = g.checkers.values.iterator.collect {
      case ch if (ch.patterns ++ ch.keywords).exists(_.contains(",")) =>
        s"${ch.property}: checker patterns and keywords must not contain ',' (clad splits on it)" }
    val membership = g.projects.keys.iterator.collect {
      case p if listedProjects.count(_ == p) != 1 => s"project $p must be in exactly one department" }
    val contradictions = g.projects.keys.iterator.flatMap { p =>
      g.path(p).groupBy(_.property).collect { case (prop, cs) if cs.map(_.ob).distinct.size > 1 =>
        s"$p: contradiction, $prop is both obligatory and prohibited" } }
    val a = g.raw.value.get("approval").collect { case o: ujson.Obj => o.value }.getOrElse(Map.empty)
    val approvedBy = Option.when(a.get("status").contains(ujson.Str("approved"))) {
      val by = a.get("approved_by").collect { case ujson.Str(s) => s }.getOrElse("")
      Option.when(!(agent.get(by).exists(_.kind == "human") && g.centralApprovers.contains(by)))(
        s"approval approved_by $by must be a human central approver") }.flatten
    (perConstraint ++ commas ++ membership ++ contradictions ++ approvedBy.iterator).nextOption()
```

- [ ] **Step 5: Run.** `sbt "gateway-test/testOnly clad.gateway.config.GovernanceSpec"` — Expected: all pass. Then `sbt test` — Expected: everything green.
- [ ] **Step 6: Commit** `feat(gateway): module skeleton and the adopter's constraint hierarchy`.

---

### Task 2: Templates, models, attestations, runtime values and the startup check

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/config/{Templates,Attestations,Runtime,GatewayConfig}.scala`
- Create fixtures: `app/gateway-test/src/test/resources/governance/{models.yaml,attestations.yaml}` and `prompts/*.yaml` (copies from personal_finance `config/governance/`; **not** `runtime.yaml`)
- Test: `app/gateway-test/src/test/scala/clad/gateway/config/TemplatesSpec.scala`

**Interfaces:**
- Consumes: `Governance`, `YamlJson` (Task 1).
- Produces:

```scala
final case class Template(id: String, version: String, project: String, tier: String, model: String, modelTier: String,
                          text: String, responseFormat: String, evidenceFormat: String, slots: Map[String, String])
final case class Models(provider: String, tiers: Map[String, String], statuses: Map[String, String]):
  def statusOf(id: String): String                         // "unapproved" when absent
final class Templates(val byId: Map[String, Template], val models: Models):
  def get(id: String): Option[Template]; def all: Vector[Template]; def modelFor(tier: String): Option[String]
  def metadata(t: Template, model: String): Map[String, String]   // template_id, template_version, project, provider, model, model_registry_status, response_format
object Templates:
  def render(t: Template, vars: Map[String, String]): Either[String, String]
  def load(dir: Path, models: Models, gov: Governance): Either[String, Templates]
  def loadModels(path: Path): Either[String, Models]
final case class Attestation(templateId: String, version: String, attestor: String, attestedAt: java.time.Instant, rationale: String)
final class Attestations(rows: Vector[Attestation]) { def get(templateId: String, version: String): Option[Attestation] }
object Attestations { def load(path: Path, humans: Set[String]): Either[String, Attestations] }
final case class Runtime(chainLockKey: Long, upstreamRetries: Int, upstreamTimeoutSeconds: Int, upstreamRetryWaitMillis: Int)
object Runtime { def fromJson(v: ujson.Value): Either[String, Runtime]; def load(path: Path): Either[String, Runtime] }
final case class GatewayConfig(governance: Governance, templates: Templates, attestations: Attestations, runtime: Runtime)
object GatewayConfig { def load(dir: Path): Either[String, GatewayConfig]; def loadWith(dir: Path, runtime: Runtime): Either[String, GatewayConfig] }
```

`runtime.yaml` keys read: `chain_lock_key`, `upstream_retries`, `upstream_timeout_seconds`, `upstream_retry_wait_ms`. A missing key is a `Left` naming it; there are no defaults.

- [ ] **Step 1: Copy fixtures** from `/Users/david/workspace/personal_finance-clad-enforcement/config/governance/`: `models.yaml`, `attestations.yaml` and `prompts/*.yaml`.

- [ ] **Step 2: Failing tests** (`TemplatesSpec.scala`):

```scala
package clad.gateway.config

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Path, Paths}

object Fixtures:
  val dir: Path = Paths.get(getClass.getResource("/governance").toURI)
  val runtime: Runtime = Runtime(chainLockKey = 7243001L, upstreamRetries = 1, upstreamTimeoutSeconds = 5, upstreamRetryWaitMillis = 10)
  def config: GatewayConfig = GatewayConfig.loadWith(dir, runtime).fold(e => throw AssertionError(e), identity)
  val sample: Map[String, String] = Map("catalog" -> "- tax_drag_ratio (dimensions: scenario, year)",
    "vanna_context" -> "Tables: gold_scenario_summary(scenario, year)", "today" -> "2026-10-16", "schema" -> "person: name")

class TemplatesSpec extends AnyFunSuite:
  val t: Templates = Fixtures.config.templates
  def vars(x: Template) = Fixtures.sample.filter((k, _) => x.slots.contains(k))

  test("all seven templates load, ids match file names, tiers come from the project") {
    assert(t.all.map(_.id) == Vector("agent.answer", "agent.plan_metrics", "agent.sql", "assistant.general",
      "doc.extract_1099", "planner.chat", "planner.interview"))
    assert(t.get("planner.chat").map(_.tier).contains("Critical") && t.get("assistant.general").map(_.tier).contains("Standard"))
    assert(t.get("assistant.general").map(_.model).contains("openai/gpt-oss-20b"))
  }

  test("slots in text equal declared slots") {
    val slot = """\{\{(\w+)\}\}""".r
    t.all.foreach(x => assert(slot.findAllMatchIn(x.text).map(_.group(1)).toSet == x.slots.keySet, x.id))
  }

  test("render substitutes once, literally, and keeps JSON braces") {
    val chat = t.get("planner.chat").get
    val out = Templates.render(chat, Map("today" -> "2026-10-16", "schema" -> "uses {{today}} and $1")).fold(fail(_), identity)
    assert(out.contains("Today is 2026-10-16.") && out.contains("uses {{today}} and $1"))
    assert(out.contains("""{"op":"create","nodeType":"document""""))
  }

  test("render refuses missing and undeclared slot values") {
    val chat = t.get("planner.chat").get
    assert(Templates.render(chat, Map("today" -> "d")).left.exists(_.contains("missing")))
    assert(Templates.render(chat, vars(chat) + ("extra" -> "x")).left.exists(_.contains("undeclared")))
  }

  test("metadata names the model's registry status") {
    val a = t.get("agent.answer").get
    val m = t.metadata(a, a.model)
    assert(m("provider") == "deepinfra" && m("model_registry_status") == t.models.statusOf(a.model))
    assert(t.metadata(a, "someone/else")("model_registry_status") == "unapproved")
  }

  test("a non-human attestor is refused") {
    val f = java.nio.file.Files.createTempFile("att", ".yaml")
    java.nio.file.Files.writeString(f, """attestations: [{template_id: t, template_version: "1", attestor: claude, attested_at: "2026-10-16T10:00:00Z", rationale: x}]""")
    assert(Attestations.load(f, Set("david")).left.exists(_.contains("human")))
  }

  test("runtime values are required, never defaulted") {
    assert(Runtime.fromJson(ujson.Obj("chain_lock_key" -> 1)).left.exists(_.contains("upstream_retries")))
  }
```

Run: `sbt "gateway-test/testOnly clad.gateway.config.TemplatesSpec"` — Expected: compilation errors.

- [ ] **Step 3: Implement.**

`Templates.scala`:

```scala
package clad.gateway.config

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

final case class Template(id: String, version: String, project: String, tier: String, model: String, modelTier: String,
                          text: String, responseFormat: String, evidenceFormat: String, slots: Map[String, String])

final case class Models(provider: String, tiers: Map[String, String], statuses: Map[String, String]):
  def statusOf(id: String): String = statuses.getOrElse(id, "unapproved")

final class Templates(val byId: Map[String, Template], val models: Models):
  def get(id: String): Option[Template] = byId.get(id)
  def all: Vector[Template] = byId.values.toVector.sortBy(_.id)
  def modelFor(tier: String): Option[String] = models.tiers.get(tier)
  def metadata(t: Template, model: String): Map[String, String] =
    Map("template_id" -> t.id, "template_version" -> t.version, "project" -> t.project, "provider" -> models.provider,
        "model" -> model, "model_registry_status" -> models.statusOf(model), "response_format" -> t.responseFormat)

object Templates:
  private val Slot = """\{\{(\w+)\}\}""".r

  /** One pass over the template text: slot values are inserted literally and never re-expanded. */
  def render(t: Template, vars: Map[String, String]): Either[String, String] =
    val missing = t.slots.keySet -- vars.keySet
    val extra = vars.keySet -- t.slots.keySet
    if missing.nonEmpty then Left(s"${t.id}: missing slot values ${missing.toVector.sorted.mkString(", ")}")
    else if extra.nonEmpty then Left(s"${t.id}: undeclared slots ${extra.toVector.sorted.mkString(", ")}")
    else Right(Slot.replaceAllIn(t.text, m => Regex.quoteReplacement(vars(m.group(1)))))

  def loadModels(path: Path): Either[String, Models] =
    try
      val o = YamlJson.read(path).obj
      val rows = Seq("approved", "classifier").flatMap(k => o.get(k).map(_.arr.toSeq).getOrElse(Seq.empty))
      Right(Models(o("provider").str, o("tiers").obj.map((k, v) => k -> v.str).toMap,
        rows.map(r => r("id").str -> r("status").str).toMap))
    catch case e: NoSuchElementException => Left(s"models.yaml: missing field ${e.getMessage}")

  def load(dir: Path, models: Models, gov: Governance): Either[String, Templates] =
    val files = Files.list(dir).iterator().asScala.filter(_.toString.endsWith(".yaml")).toVector.sortBy(_.toString)
    files.foldLeft[Either[String, Map[String, Template]]](Right(Map.empty)) { (acc, f) =>
      acc.flatMap { m =>
        try
          val o = YamlJson.read(f).obj
          val id = o("id").str
          val file = f.getFileName.toString.stripSuffix(".yaml")
          if id != file then Left(s"$file.yaml: id $id does not match the file name")
          else
            val project = o("project").str
            for
              p <- gov.projects.get(project).toRight(s"$id: unknown project $project")
              model <- models.tiers.get(o("model_tier").str).toRight(s"$id: unknown model tier ${o("model_tier").str}")
            yield m + (id -> Template(id, o("version") match { case ujson.Str(s) => s; case v => v.render() },
              project, p.tier, model, o("model_tier").str, o("text").str.stripTrailing, o("response_format").str,
              o("evidence_format").str, o.get("slots").collect { case s: ujson.Obj => s.value.map((k, v) => k -> v.str).toMap }.getOrElse(Map.empty)))
        catch case e: NoSuchElementException => Left(s"${f.getFileName}: missing field ${e.getMessage}")
      }
    }.map(Templates(_, models))
```

`Attestations.scala`:

```scala
package clad.gateway.config

import java.nio.file.Path
import java.time.Instant

final case class Attestation(templateId: String, version: String, attestor: String, attestedAt: Instant, rationale: String)

final class Attestations(rows: Vector[Attestation]):
  private val byKey = rows.map(a => (a.templateId, a.version) -> a).toMap
  def get(templateId: String, version: String): Option[Attestation] = byKey.get((templateId, version))

object Attestations:
  def load(path: Path, humans: Set[String]): Either[String, Attestations] =
    try
      val rows = YamlJson.read(path).obj.get("attestations").map(_.arr.toVector).getOrElse(Vector.empty).map { r =>
        Attestation(r("template_id").str, r("template_version") match { case ujson.Str(s) => s; case v => v.render() },
          r("attestor").str, Instant.parse(r("attested_at").str), r("rationale").str) }
      rows.find(a => !humans.contains(a.attestor)) match
        case Some(a) => Left(s"attestation of ${a.templateId}@${a.version}: attestor ${a.attestor} must be a declared human (an LLM is never an attestor)")
        case None    => Right(Attestations(rows))
    catch
      case e: NoSuchElementException => Left(s"attestations.yaml: missing field ${e.getMessage}")
      case e: java.time.format.DateTimeParseException => Left(s"attestations.yaml: ${e.getMessage}")
```

`Runtime.scala`:

```scala
package clad.gateway.config

import java.nio.file.Path

/** Every number the gateway uses, from the adopter's runtime.yaml (no defaults: a missing key is an error). */
final case class Runtime(chainLockKey: Long, upstreamRetries: Int, upstreamTimeoutSeconds: Int, upstreamRetryWaitMillis: Int)

object Runtime:
  def load(path: Path): Either[String, Runtime] = fromJson(YamlJson.read(path))

  def fromJson(v: ujson.Value): Either[String, Runtime] =
    def num(k: String): Either[String, Double] = v.obj.get(k).collect { case ujson.Num(n) => n }.toRight(s"runtime.yaml: $k is required")
    for
      lock <- num("chain_lock_key")
      retries <- num("upstream_retries")
      wait <- num("upstream_timeout_seconds")
      pause <- num("upstream_retry_wait_ms")
    yield Runtime(lock.toLong, retries.toInt, wait.toInt, pause.toInt)
```

`GatewayConfig.scala`:

```scala
package clad.gateway.config

import java.nio.file.Path

final case class GatewayConfig(governance: Governance, templates: Templates, attestations: Attestations, runtime: Runtime)

object GatewayConfig:
  def load(dir: Path): Either[String, GatewayConfig] = Runtime.load(dir.resolve("runtime.yaml")).flatMap(loadWith(dir, _))

  def loadWith(dir: Path, runtime: Runtime): Either[String, GatewayConfig] =
    for
      gov <- Governance.load(dir.resolve("constraints.yaml"))
      models <- Templates.loadModels(dir.resolve("models.yaml"))
      templates <- Templates.load(dir.resolve("prompts"), models, gov)
      attestations <- Attestations.load(dir.resolve("attestations.yaml"), gov.humans)
    yield GatewayConfig(gov, templates, attestations, runtime)
```

- [ ] **Step 4: Run** the spec, then `sbt test`. Expected: green.
- [ ] **Step 5: Commit** `feat(gateway): governed templates, model registry, attestations and runtime values`.

(The startup check needs the EPG evaluator; it is added in Task 6.)

---

### Task 3: Signing keys

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/keys/Keys.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/keys/KeysSpec.scala`

**Interfaces:**
- Produces: `object Keys { def generate(dir: Path, role: String): Either[String, Boolean]; def load(dir: Path, role: String): Either[String, Ed25519KeyManagement]; def keyIdOf(publicPem: String): Either[String, String]; def publicPem(dir: Path, role: String): Either[String, String] }`. Files are `<role>.ed25519` (PKCS#8 PEM, mode 600) and `<role>.ed25519.pub.pem` (X.509 SubjectPublicKeyInfo PEM), the layout personal_finance's existing `secrets/governance/` already uses. Key id = first 16 hex characters of sha256(public key DER). Roles: `components`, `supervisor`.

- [ ] **Step 1: Failing test**

```scala
package clad.gateway.keys

import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, attribute}
import java.nio.file.attribute.PosixFilePermission

class KeysSpec extends AnyFunSuite:
  test("generate once, load, sign, verify; the private key is mode 600; the key id is 16 hex") {
    val dir = Files.createTempDirectory("keys")
    assert(Keys.generate(dir, "components") == Right(true))
    assert(Keys.generate(dir, "components") == Right(false))
    val perms = Files.getPosixFilePermissions(dir.resolve("components.ed25519"))
    assert(perms == java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    val kms = Keys.load(dir, "components").fold(fail(_), identity)
    val pem = Keys.publicPem(dir, "components").fold(fail(_), identity)
    assert(kms.keyId.matches("[0-9a-f]{16}") && Keys.keyIdOf(pem) == Right(kms.keyId))
    val sig = kms.sign("sha256:abc".getBytes("UTF-8")).fold(e => fail(e.toString), identity)
    assert(kms.verify("sha256:abc".getBytes("UTF-8"), sig) == Right(true))
  }
```

Also add, as a second test, that a key pair written by personal_finance's Python `generate_keypair` loads: copy one public/private pair from a temp run of `cd /Users/david/workspace/personal_finance-clad-enforcement/stack && uv run python -c "from pathlib import Path; from src.governance.signing import generate_keypair; generate_keypair(Path('/tmp/pfkeys'), 'components')"` into `app/gateway-test/src/test/resources/keys/` and assert `Keys.load(...).isRight` and that `Keys.keyIdOf` equals the Python `key_id_of_public_pem` value (record it in the test). The live pod's keys must keep working after the switch.

Run: `sbt "gateway-test/testOnly clad.gateway.keys.KeysSpec"` — Expected: compilation error.

- [ ] **Step 2: Implement**

```scala
package clad.gateway.keys

import clad.audit.Ed25519KeyManagement
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.security.{KeyFactory, KeyPairGenerator, MessageDigest}
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Keys:
  private def pem(kind: String, der: Array[Byte]): String =
    s"-----BEGIN $kind-----\n${Base64.getMimeEncoder(64, "\n".getBytes("UTF-8")).encodeToString(der)}\n-----END $kind-----\n"

  private def der(pemText: String): Either[String, Array[Byte]] =
    try Right(Base64.getDecoder.decode(pemText.linesIterator.filterNot(_.startsWith("-----")).mkString.trim))
    catch case e: IllegalArgumentException => Left(s"malformed PEM: ${e.getMessage}")

  def keyIdOf(publicPem: String): Either[String, String] = der(publicPem).flatMap { d =>
    try
      val spki = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(d)).getEncoded
      Right(MessageDigest.getInstance("SHA-256").digest(spki).map("%02x".format(_)).mkString.substring(0, 16))
    catch case e: java.security.GeneralSecurityException => Left(s"not an Ed25519 public key: ${e.getMessage}")
  }

  def publicPem(dir: Path, role: String): Either[String, String] =
    val p = dir.resolve(s"$role.ed25519.pub.pem")
    if Files.exists(p) then Right(Files.readString(p)) else Left(s"$p is missing (run `clad-gateway keys`)")

  def load(dir: Path, role: String): Either[String, Ed25519KeyManagement] =
    val priv = dir.resolve(s"$role.ed25519")
    for
      pub <- publicPem(dir, role)
      privText <- if Files.exists(priv) then Right(Files.readString(priv)) else Left(s"$priv is missing (run `clad-gateway keys`)")
      kid <- keyIdOf(pub)
      kms <- Ed25519KeyManagement.fromPrivatePem(privText, pub, kid).left.map(_.toString)
    yield kms

  def generate(dir: Path, role: String): Either[String, Boolean] =
    val priv = dir.resolve(s"$role.ed25519")
    if Files.exists(priv) then Right(false)
    else
      try
        Files.createDirectories(dir)
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        Files.createFile(priv, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        Files.writeString(priv, pem("PRIVATE KEY", kp.getPrivate.getEncoded), StandardOpenOption.TRUNCATE_EXISTING)
        Files.writeString(dir.resolve(s"$role.ed25519.pub.pem"), pem("PUBLIC KEY", kp.getPublic.getEncoded))
        Right(true)
      catch case e: java.io.IOException => Left(s"cannot write keys to $dir: ${e.getMessage}")
```

(`substring(0, 16)` is a key-id width fixed by the spec, not a limit. If the invented-limits gate flags it, define `val KeyIdHexLength = 16 // spec section 5: key ids are 16 hex` and use that.)

- [ ] **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): Ed25519 component and supervisor keys`.

---

### Task 4: Schema migration and the test database

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/store/Migrate.scala`, `app/gateway/src/main/scala/clad/gateway/store/Jdbc.scala`
- Create: `app/gateway-test/src/test/scala/clad/gateway/store/TestDatabase.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/store/MigrateSpec.scala`

**Interfaces:**
- Produces: `object Migrate { val Tables: Vector[String]; def statements(writerPw: String, readerPw: String): Either[String, Vector[String]]; def run(adminUrl: String, writerPw: String, readerPw: String): Either[String, Unit] }`; `object Jdbc { def withConnection[A](url: String)(f: java.sql.Connection => A): Either[String, A]; def withTransaction[A](url: String)(f: java.sql.Connection => A): Either[String, A] }`; test-side `TestDatabase.fresh(): TestDb(adminUrl: String, writerUrl: String, readerUrl: String)`.
- Roles: `clad_gov_owner` (NOLOGIN), `clad_gov_writer`, `clad_gov_reader`. Schemas: `governance`, `mdr`. Tables and triggers exactly as personal_finance's `src/governance/store/migrate.py` (spec section 8), with the `pf_gov_*` role names replaced.

- [ ] **Step 1: TestDatabase** (test helper):

```scala
package clad.gateway.store

import java.util.concurrent.atomic.AtomicInteger

final case class TestDb(adminUrl: String, writerUrl: String, readerUrl: String)

/** A fresh, migrated database per call on the server named by CLAD_TEST_PG_ADMIN_URL (a superuser JDBC URL).
  * Names start clad_gw_test_ so a live store can never be the target. */
object TestDatabase:
  val WriterPw = "testwriterpw"
  val ReaderPw = "testreaderpw"
  private val n = AtomicInteger()

  lazy val admin: String = sys.env.getOrElse("CLAD_TEST_PG_ADMIN_URL",
    throw AssertionError("CLAD_TEST_PG_ADMIN_URL is not set: point it at a Postgres superuser, e.g. jdbc:postgresql://localhost:5432/postgres?user=david"))

  private def withDb(url: String, db: String): String =
    val (base, query) = url.split("\\?", 2) match { case Array(b, q) => (b, s"?$q"); case Array(b) => (b, "") }
    s"${base.substring(0, base.lastIndexOf('/'))}/$db$query"

  private def withUser(url: String, user: String, pw: String): String =
    val base = url.split("\\?", 2)(0)
    s"$base?user=$user&password=$pw"

  def fresh(): TestDb =
    val db = s"clad_gw_test_${ProcessHandle.current().pid()}_${n.incrementAndGet()}"
    Jdbc.withConnection(admin) { c =>
      c.createStatement().execute(s"DROP DATABASE IF EXISTS $db WITH (FORCE)")
      c.createStatement().execute(s"CREATE DATABASE $db")
    }.fold(e => throw AssertionError(e), identity)
    val dbAdmin = withDb(admin, db)
    Migrate.run(dbAdmin, WriterPw, ReaderPw).fold(e => throw AssertionError(e), identity)
    TestDb(dbAdmin, withUser(dbAdmin, "clad_gov_writer", WriterPw), withUser(dbAdmin, "clad_gov_reader", ReaderPw))
```

- [ ] **Step 2: Failing tests** (`MigrateSpec.scala`):

```scala
package clad.gateway.store

import org.scalatest.funsuite.AnyFunSuite

class MigrateSpec extends AnyFunSuite:
  test("every table gets the refuse-change triggers; the writer gets INSERT and SELECT only") {
    val text = Migrate.statements("w", "r").fold(fail(_), identity).mkString("\n")
    Migrate.Tables.foreach { t =>
      assert(text.contains(s"BEFORE UPDATE OR DELETE ON $t"), t)
      assert(text.contains(s"BEFORE TRUNCATE ON $t"), t)
    }
    assert(text.contains("GRANT INSERT, SELECT ON ALL TABLES IN SCHEMA governance TO clad_gov_writer"))
  }

  test("a password that is not alphanumeric is refused (it is inlined into ALTER ROLE)") {
    assert(Migrate.statements("x'; DROP", "r").isLeft)
  }

  test("migrate is idempotent; the writer cannot change rows and the triggers stop even the superuser") {
    val db = TestDatabase.fresh()
    assert(Migrate.run(db.adminUrl, TestDatabase.WriterPw, TestDatabase.ReaderPw) == Right(()))
    Jdbc.withConnection(db.writerUrl)(_.createStatement().execute(
      "INSERT INTO governance.gil VALUES (gen_random_uuid(), now(), 'agent', 'test', 'Critical', '{}')")).fold(fail(_), identity)
    for stmt <- Seq("UPDATE governance.gil SET tier = 'Low'", "DELETE FROM governance.gil", "TRUNCATE governance.gil") do
      assert(Jdbc.withConnection(db.writerUrl)(_.createStatement().execute(stmt)).isLeft, stmt)
      assert(Jdbc.withConnection(db.adminUrl)(_.createStatement().execute(stmt)).left.exists(_.contains("append-only")), stmt)
  }
```

Run: `CLAD_TEST_PG_ADMIN_URL=jdbc:postgresql://localhost:5432/postgres?user=david sbt "gateway-test/testOnly clad.gateway.store.MigrateSpec"` — Expected: compilation errors.

- [ ] **Step 3: Implement.**

`Jdbc.scala`:

```scala
package clad.gateway.store

import java.sql.{Connection, DriverManager, SQLException}

object Jdbc:
  def withConnection[A](url: String)(f: Connection => A): Either[String, A] =
    try
      val c = DriverManager.getConnection(url)
      try Right(f(c)) finally c.close()
    catch case e: SQLException => Left(s"${e.getSQLState}: ${e.getMessage}")

  def withTransaction[A](url: String)(f: Connection => A): Either[String, A] =
    withConnection(url) { c =>
      c.setAutoCommit(false)
      try
        val a = f(c)
        c.commit()
        a
      catch case e: SQLException => c.rollback(); throw e
    }
```

`Migrate.scala`: port `migration_statements` from personal_finance `src/governance/store/migrate.py` line for line: the `DDL` list (roles, schemas, the nine tables, `governance.refuse_change()`), `_ownership_and_grants()`, and the two `ALTER ROLE ... PASSWORD` statements, with `pf_gov_` → `clad_gov_`. `Tables` is the Python `TABLES` list. Passwords must match `[A-Za-z0-9]+` (else `Left("role passwords must be alphanumeric")`) and are inlined as `'<pw>'`. `run` executes each statement with `Jdbc.withConnection(adminUrl)` (autocommit), in order, and returns the first error.

- [ ] **Step 4: Run** with `CLAD_TEST_PG_ADMIN_URL` set. Expected: pass. Then `sbt test` with the variable set.
- [ ] **Step 5: Commit** `feat(gateway): append-only governance schema with least-privilege roles`.

---

### Task 5: Stores: GIL, the linked chain with anchors, degraded records, configs, artifacts

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/store/{GatewayStore,PgStore}.scala`
- Create: `app/gateway-test/src/test/scala/clad/gateway/store/MemoryStore.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/store/PgStoreSpec.scala`

**Interfaces:**
- Consumes: `Jdbc`, `TestDatabase` (Task 4); clad `AuditRecord`, `SignedAuditRecord`, `AuditRecordCodec`, `ChainAnchor`, `ChainAnchorStore`, `GilEntry`, `SignedDegradedRecord`, `DegradedRecordCodec`, `KeyManagementService`.
- Produces:

```scala
trait GatewayStore:
  def register(entry: GilEntry): Either[String, Unit]     // metadata must hold "project", "principal", "tier"
  def appendLinked(build: Option[String] => AuditRecord, kms: KeyManagementService): Either[String, SignedAuditRecord]
  def degraded(r: SignedDegradedRecord): Either[String, Unit]
  def putConfig(digest: String, project: String, json: String): Either[String, Unit]   // idempotent
  def putArtifact(digest: String, json: String): Either[String, Unit]                   // idempotent
final class PgStore(writerUrl: String, lockKey: Long, anchors: ChainAnchorStore, anchorKms: KeyManagementService) extends GatewayStore
final class MemoryStore extends GatewayStore   // test helper: gil, lines, degradedLines, configs, artifacts as public buffers
```

`appendLinked`, in one transaction:
1. `SELECT pg_advisory_xact_lock(?)` with `lockKey`;
2. read the head with `SELECT digest, (SELECT count(*) FROM governance.audit_record) FROM governance.audit_record WHERE seq = (SELECT max(seq) FROM governance.audit_record)` (no `LIMIT`);
3. `build(head)`, then `SignedAuditRecord.sign` (a `Left` aborts);
4. insert `(interaction_id, component, digest, previous_digest, line = AuditRecordCodec.encode(signed))`;
5. commit, then write `ChainAnchor(count + 1, digest, Instant.now())` signed with `anchorKms` to `anchors`.

A failed anchor write is printed to stderr as `GOVERNANCE_ANCHOR_FAILED <reason>` and does not fail the append: the record is stored, and one anchor is lost.

- [ ] **Step 1: Failing tests** (`PgStoreSpec.scala`):

```scala
package clad.gateway.store

import clad.audit.*
import clad.core.*
import clad.evaluation.MechanicalDetail
import clad.integrity.GilEntry
import clad.runtime.*
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

class PgStoreSpec extends AnyFunSuite:
  val (kms, pub) = Ed25519KeyManagement.generate("k1")
  val Threads = 20

  def record(prev: Option[String]): AuditRecord =
    val t = Instant.parse("2026-10-16T12:00:00Z")
    AuditRecord("sha256:a", Vector(AuditEntry(Constraint.Obligation(PropertyId.unsafe("p"), Level.Enterprise), "1",
      EvaluabilityClass.Mechanical, true, MechanicalDetail(true), t)), "sha256:c", t, prev,
      Some(InteractionId(UUID.randomUUID().toString)), Some("epg"), Some(Surface.Prompt), Some("pass"))

  def store(db: TestDb) = PgStore(db.writerUrl, 7243001L, ChainAnchorStore(Files.createTempDirectory("anchors")), kms)

  def lines(db: TestDb): Vector[SignedAuditRecord] =
    Jdbc.withConnection(db.readerUrl) { c =>
      val rs = c.createStatement().executeQuery("SELECT line FROM governance.audit_record ORDER BY seq")
      Iterator.continually(rs).takeWhile(_.next()).map(r => AuditRecordCodec.decode(r.getString(1)).fold(fail(_), identity)).toVector
    }.fold(fail(_), identity)

  test("concurrent appends form one chain") {
    val db = TestDatabase.fresh()
    val s = store(db)
    given ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(Threads))
    Await.result(Future.sequence((1 to Threads).map(_ => Future(s.appendLinked(record, kms)))), Duration.Inf)
      .foreach(r => assert(r.isRight, r))
    val chain = lines(db)
    assert(chain.size == Threads && chain.headOption.exists(_.record.previousDigest.isEmpty))
    chain.zip(chain.drop(1)).foreach((a, b) => assert(b.record.previousDigest.contains(a.record.digest)))
    assert(chain.forall(_.isAuthentic(kms) == Right(true)))
  }

  test("a second store instance (a restart) continues the stored chain") {
    val db = TestDatabase.fresh()
    store(db).appendLinked(record, kms).fold(fail(_), identity)
    val second = store(db).appendLinked(record, kms).fold(fail(_), identity)
    assert(second.record.previousDigest.contains(lines(db).headOption.map(_.record.digest).get))
  }

  test("the anchor names the chain length and head") {
    val db = TestDatabase.fresh()
    val dir = Files.createTempDirectory("anchors")
    val s = PgStore(db.writerUrl, 7243001L, ChainAnchorStore(dir), kms)
    val last = (1 to 3).map(_ => s.appendLinked(record, kms).fold(fail(_), identity)).last
    val anchor = ChainAnchorStore(dir).read.get.get
    assert(anchor.anchor.count == 3 && anchor.anchor.headDigest == last.record.digest)
  }

  test("register, configs and artifacts are stored; configs and artifacts are idempotent") {
    val db = TestDatabase.fresh()
    val s = store(db)
    val iid = InteractionId(UUID.randomUUID().toString)
    assert(s.register(GilEntry(iid, Instant.now(), Map("project" -> "agent", "principal" -> "api", "tier" -> "Critical",
      "template_id" -> "agent.answer"))) == Right(()))
    assert(s.putConfig("sha256:c", "agent", "{}") == Right(()) && s.putConfig("sha256:c", "agent", "{}") == Right(()))
    assert(s.putArtifact("sha256:a", "{}") == Right(()) && s.putArtifact("sha256:a", "{}") == Right(()))
    assert(s.register(GilEntry(iid, Instant.now(), Map("project" -> "agent"))).left.exists(_.contains("principal")))
  }
```

`MemoryStore.scala` (test helper) implements the same contract in memory with `synchronized` buffers (`gil: ArrayBuffer[GilEntry]`, `lines: ArrayBuffer[SignedAuditRecord]`, `degradedLines: ArrayBuffer[SignedDegradedRecord]`, `configs: mutable.Map[String, String]`, `artifacts: mutable.Map[String, String]`), plus `var failRegister: Boolean = false`, which makes `register` return `Left("db down")`.

Run with `CLAD_TEST_PG_ADMIN_URL` — Expected: compilation errors.

- [ ] **Step 2: Implement `GatewayStore` and `PgStore`** as specified above. SQL:
  - `register`: `INSERT INTO governance.gil VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb)`, with the metadata map rendered by `ujson.Obj.from(...)`. A missing `project`, `principal` or `tier` returns `Left("GIL metadata needs <key>")` without touching the database.
  - `degraded`: `INSERT INTO governance.degraded_record (interaction_id, line) VALUES (?::uuid, ?)` with `DegradedRecordCodec.encode`.
  - `putConfig` / `putArtifact`: `INSERT … ON CONFLICT (digest) DO NOTHING`. Config bodies are stored as UTF-8 bytes.
- [ ] **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): Postgres stores with a locked, anchored hash chain`.

---

### Task 6: EPG evaluation and the startup check

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/epg/{Epg,StartupCheck}.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/epg/EpgSpec.scala`

**Interfaces:**
- Consumes: `GatewayConfig`, `Template`, `Templates` (Task 2); clad `ConfigLoader.loadFromString`, `ConfigLoader.buildHierarchy`, `PromptEvaluator.evaluate`, `PromptArtifact`, `EvidenceSet`, `ProceduralEvidence`, `AuditEntry`.
- Produces:

```scala
final case class EpgResult(entries: Vector[AuditEntry], artifactDigest: String, artifactJson: String,
                           configDigest: String, configJson: String):
  def blocked: Boolean                                   // any entry unsatisfied
final class Epg private (cfg: GatewayConfig, hierarchies: Map[String, clad.evaluation.EvaluableHierarchy]):
  def evaluate(t: Template, rendered: String, model: String, at: java.time.Instant): Either[String, EpgResult]
object Epg { def build(cfg: GatewayConfig): Either[String, Epg] }
object StartupCheck:
  final case class Report(errors: Vector[String], warnings: Vector[String])
  val DeploymentStateKeys: Set[String]                   // Set("model_registry_status")
  def run(cfg: GatewayConfig, epg: Epg): Report
```

- `artifactJson` is the bundle artifact file (conformance spec 4.3): `{"content": rendered, "metadata": Templates.metadata(t, model) + ("project" -> t.project)}`, rendered with `ujson.write`. `artifactDigest = Canonical.sha256(rendered)`, the digest of the content, as conformance computes it.
- Procedural evidence: for every procedural constraint on the path, use the template version's attestation, satisfied. If there is none, `ProceduralEvidence(prop, attestor = "", satisfied = false, attestedAt = Instant.EPOCH, rationale = s"no attestation for ${t.id}@${t.version}")`.
- Entries: one `AuditEntry` per `ConstraintResult` in the report (mechanical → `EvaluabilityClass.Mechanical`, procedural → `Procedural`), all timestamped `at`, sorted by property name.
- The hierarchy is built once per project in `Epg.build`, from `cfg.governance.configJson(project)` through `ConfigLoader.loadFromString` and `buildHierarchy`. That is the path conformance re-evaluates, so the two agree by construction.
- `StartupCheck.run`: render each template with each slot's description as its value, evaluate, then classify:
  - an unsatisfied **mechanical** entry is an **error**, unless its checker is structural on a key in `DeploymentStateKeys`, in which case it's a **warning**;
  - an unsatisfied **procedural** entry is a **warning** ("not attested").

- [ ] **Step 1: Failing tests** (`EpgSpec.scala`):

```scala
package clad.gateway.epg

import clad.gateway.config.Fixtures
import org.scalatest.funsuite.AnyFunSuite
import java.time.Instant

class EpgSpec extends AnyFunSuite:
  val cfg = Fixtures.config
  val epg = Epg.build(cfg).fold(fail(_), identity)
  val at = Instant.parse("2026-10-16T12:00:00Z")
  val answer = cfg.templates.get("agent.answer").get

  test("one entry per constraint on the path; a prompt with an SSN fails contains_pii") {
    val r = epg.evaluate(answer, answer.text + " Call 123-45-6789.", answer.model, at).fold(fail(_), identity)
    assert(r.entries.size == cfg.governance.path("agent").size)
    val pii = r.entries.find(_.constraint.property.value == "contains_pii").get
    assert(!pii.satisfied && r.blocked)
  }

  test("an unattested template gives an unsatisfied procedural entry; pending models fail approved_model") {
    val r = epg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    assert(r.entries.exists(e => e.constraint.property.value == "compliance_review" && !e.satisfied))
    assert(r.entries.exists(e => e.constraint.property.value == "approved_model" && !e.satisfied))
  }

  test("clad's own re-evaluation of the stored artifact agrees with the record") {
    val r = epg.evaluate(answer, answer.text, answer.model, at).fold(fail(_), identity)
    val art = ujson.read(r.artifactJson)
    val hc = clad.config.ConfigLoader.loadFromString(r.configJson).flatMap(clad.config.ConfigLoader.buildHierarchy).fold(e => fail(e.mkString), identity)
    val ev = clad.evaluation.EvidenceSet(r.entries.collect { case clad.runtime.AuditEntry(c, _, _, s, clad.evaluation.ProceduralDetail(a, t, why), _) =>
      c.property -> clad.evaluation.ProceduralEvidence(c.property, a, s, t, why) }.toMap)
    val report = clad.evaluation.PromptEvaluator.evaluate(clad.evaluation.PromptArtifact(art("content").str,
      art("metadata").obj.map((k, v) => k -> v.str).toMap), hc, ev, clad.core.Level.Project, at).fold(e => fail(e.toString), identity)
    val expected = report.mechanicalResults.map(c => c.constraint -> c.satisfied).toMap
    val recorded = r.entries.filter(_.evaluabilityClass == clad.runtime.EvaluabilityClass.Mechanical).map(e => e.constraint -> e.satisfied).toMap
    assert(expected == recorded)
  }

  test("the startup check passes every template's text; approvals and attestations are warnings") {
    val rep = StartupCheck.run(cfg, epg)
    assert(rep.errors.isEmpty, rep.errors)
    assert(rep.warnings.exists(_.contains("approved_model")) && rep.warnings.exists(_.contains("attest")))
  }
```

- [ ] **Step 2: Implement** as specified. **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): EPG with clad's evaluator and the startup check`.

---

### Task 7: The gateway pipeline

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/pipeline/{Model,Gateway,Principal}.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/pipeline/GatewaySpec.scala`

**Interfaces:**
- Consumes: `GatewayConfig` (2), `Keys`/`KeyManagementService` (3), `GatewayStore`, `MemoryStore` (5), `Epg` (6); clad `GilEntry`, `AuditRecord`, `Surface`, `DegradedAuditRecord`, `SignedDegradedRecord`, `FailurePosture`, `ComponentId`, `InteractionId`.
- Produces:

```scala
final case class GovernedRequest(templateId: String, principal: String, session: Option[String], messages: Vector[ujson.Obj],
                                 vars: Map[String, String], evidence: Option[ujson.Value], modelTier: Option[String],
                                 options: ujson.Obj)   // tools, tool_choice, response_format, temperature, max_tokens: passed through
enum GatewayResult:
  case Delivered(interactionId: String, outcome: String, response: ujson.Value)
  case Blocked(interactionId: String, reason: String)
  case Rejected(reason: String)
  case Unavailable(interactionId: Option[String], reason: String)
trait Upstream { def complete(body: ujson.Obj, interactionId: String): Either[String, ujson.Value] }   // Left: unavailable
trait PassthroughUpstream { def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)] }
object Principal { def pseudonymise(p: String): String }
final class Gateway(cfg: GatewayConfig, epg: Epg, store: GatewayStore, components: KeyManagementService,
                    supervisor: KeyManagementService, upstream: Upstream, passthrough: PassthroughUpstream,
                    containment: java.nio.file.Path, clock: () => java.time.Instant):
  def complete(req: GovernedRequest): GatewayResult
  def passthrough(name: String, path: String, templateId: String, principal: String, session: Option[String],
                  body: String): GatewayResult            // Delivered.response = ujson.Obj("status" -> n, "body" -> text)
  def contain(project: String, reason: String): Either[String, Unit]
  def release(project: String): Either[String, Unit]
```

Pipeline for `complete` (spec section 7, without ROC):
1. **Check:** a `system` message, an unknown template, or `Templates.render` failing → `Rejected`.
2. **Pseudonymise:** `Principal.pseudonymise` replaces each `[^\s:@]+@[^\s:@]+` with `user-` + the first 16 hex of sha256 of the lower-cased match.
3. **Model:** `modelTier.flatMap(cfg.templates.modelFor)` or the template's model. An unknown tier → `Rejected`.
4. **Contain:** if `containment` (a JSON object file `{"<project>": "<reason>"}`) names the project: register, append a record with component `gateway`, surface `Delivery`, outcome `block:contained`, no entries, artifact digest of the rendered prompt and config digest of the project; return `Blocked(iid, "contained")`.
5. **Register:** `GilEntry(iid, now, metadata)`, where the metadata holds `project`, `principal`, `tier`, `template_id`, `template_version`, `model`, plus, when present, `session`, `temperature`, `max_tokens` and `evidence_digest` (`Canonical.sha256` of the evidence's `ujson.write`, or of the string itself). A failure → `Blocked(iid, "gil_unavailable")`, with nothing else done.
6. **EPG:** `epg.evaluate`; `store.putConfig` and `store.putArtifact`; append the EPG record (component `epg`, surface `Prompt`, outcome `block` or `pass`). If blocked → `Blocked(iid, <first unsatisfied property>)`.
7. **Body:** `{"model", "messages": [system: rendered] ++ messages-with-evidence}` plus `options`. Evidence is appended to the last user message as `"\n\n<data>\n" + payload + "\n</data>"`: `ujson.write(evidence)` for `rows_json`, or the text with `<data`/`</data` replaced by `&lt;data`/`&lt;/data` for `document_pages`.
8. **Upstream:**
   - A `Left`, or a reply without a non-empty `choices` array, is an upstream failure. Write a supervisor-signed degraded record (component `roc`, the posture for the tier, `failureReason = "upstream: …"`). Then Critical → `Blocked(iid, "upstream_unavailable")`, otherwise `Unavailable(Some(iid), reason)`.
   - Otherwise `Delivered(iid, "pass", reply)`.

`passthrough`: steps 1 (template must exist), 2, 5 (project from the template; its `template_id` is recorded), then forward, and an upstream failure as in step 8. There's no EPG step (spec 6.2).

Posture by tier: `Critical` → `FailClosed`, `Standard` → `FailOpenFlagged`, `Low` → `FailOpen` (`FailurePosture.actionFor` gives the action). Sign degraded records with `SignedDegradedRecord.sign(record, supervisor, ComponentId("supervisor"))`; a signing failure is a `Left` written to stderr as `GOVERNANCE_DEGRADED_UNSIGNED`. Never store an unsigned record.

- [ ] **Step 1: Failing tests** (`GatewaySpec.scala`). Use `Fixtures.config`, a `MemoryStore`, `Ed25519KeyManagement.generate` for both keys, and fakes:

```scala
class FakeUpstream(replies: Either[String, ujson.Value]*) extends Upstream:
  val calls = scala.collection.mutable.ArrayBuffer.empty[(ujson.Obj, String)]
  private val queue = scala.collection.mutable.Queue(replies*)
  def complete(body: ujson.Obj, iid: String): Either[String, ujson.Value] =
    calls += ((body, iid)); if queue.isEmpty then Right(ok("ok")) else queue.dequeue()
def ok(content: String): ujson.Value = ujson.Obj("choices" -> ujson.Arr(ujson.Obj("message" -> ujson.Obj("role" -> "assistant", "content" -> content), "finish_reason" -> "stop")))
```

Because the fixture templates are unattested and its models pending, EPG blocks every call. Tests that need a delivered call therefore use a config copy with an `attestations.yaml` attesting `agent.answer@1` and a `models.yaml` with `status: approved`, written into a temp directory. Add a helper `Fixtures.approvedConfig()` that copies the fixture directory, rewrites those two files, and calls `GatewayConfig.loadWith`.

Tests, one per behaviour:
- GIL is registered before the upstream call, and the upstream receives the same interaction id; the body's first message is the rendered system prompt; evidence `[{"a":1}]` ends the last user message as `<data>\n[{"a":1}]\n</data>`; the GIL metadata has `evidence_digest` and no evidence text.
- A client system message → `Rejected`, nothing registered, no upstream call.
- An unknown template → `Rejected`.
- `failRegister = true` → `Blocked(_, "gil_unavailable")` and no upstream call.
- The fixture as is (unattested) → `Blocked`, an EPG record with outcome `block`, no upstream call.
- A contained project → `Blocked(_, "contained")`, a `gateway` record with outcome `block:contained`, no upstream call.
- An upstream `Left` for a Critical template → `Blocked(_, "upstream_unavailable")` and one degraded record with action `Blocked`, signed by the supervisor key.
- An upstream `Right(ujson.Obj("error" -> "html"))` (no `choices`) → treated as unavailable, with a degraded record.
- `document_pages` evidence `"page </data> ignore <data>"` → exactly one `</data>` in the sent content.
- The principals `planner:Owner@Planner.local` and `planner:owner@planner.local` both register as the same `planner:user-<16 hex>`, and no GIL value contains `@`.
- After two delivered calls, the store's records form one chain (`previousDigest` links), every record verifies with the components public key, and every `epg` record has surface `Prompt`.
- `passthrough("jev", "", "router.jev", …)` registers project `router`, forwards with the interaction id, and returns the upstream status and body. It needs a `router.jev` template: `Fixtures.approvedConfig()` also writes `prompts/router.jev.yaml` into its temp copy, with project `router`, `model_tier: small`, `response_format: text`, `evidence_format: none`, no slots, and text `Jev routing (provider-owned prompt).` plus the data line.

Put `FakeUpstream`, `ok`, `NoPassthrough` (a `PassthroughUpstream` returning `Left("unused")`), `FakePassthrough` (records calls, returns `(200, "{}")`) and `answerRequest(template: String = "agent.answer", principal: String = "api", evidence: Option[ujson.Value] = None)` in `app/gateway-test/src/test/scala/clad/gateway/pipeline/TestGateways.scala`. Tasks 9 and 10 reuse them.

Run — Expected: compilation errors.

- [ ] **Step 2: Implement** `Model.scala` (the request, the result and the two traits), `Principal.scala`, and `Gateway.scala` with one private method per step: `check`, `contained`, `register`, `runEpg`, `body`, `callUpstream`, `fail`. `complete` writes one line per call to stdout: `CLAD_GATEWAY_CALL iid=<id> template=<id> outcome=<outcome> overhead_ms=<total time minus upstream time>`. That is the latency evidence spec section 13 asks for. It is measured, not enforced, in this plan.
- [ ] **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): the governed pipeline (GIL first, EPG, upstream, postures, containment)`.

---

### Task 8: Upstream HTTP clients

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/upstream/{HttpUpstream,HttpPassthrough}.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/upstream/HttpUpstreamSpec.scala`

**Interfaces:**
- Consumes: `Upstream`, `PassthroughUpstream` (7), `Runtime` (2).
- Produces: `final class HttpUpstream(baseUrl: String, apiKey: String, runtime: Runtime, sleep: Long => Unit = Thread.sleep) extends Upstream`; `final class HttpPassthrough(upstreams: Map[String, (String, String)], runtime: Runtime) extends PassthroughUpstream` (name → (base URL, key)).

Both use `java.net.http.HttpClient` with a request timeout of `runtime.upstreamTimeoutSeconds`. They send `Authorization: Bearer <key>` (the client's own token is never forwarded) and `X-Clad-Interaction-Id`. On 429 or 503 they retry up to `runtime.upstreamRetries` times, sleeping `runtime.upstreamRetryWaitMillis` between attempts. `HttpUpstream` posts to `<baseUrl>/chat/completions`:
- 2xx with a JSON body → `Right(json)`;
- anything else (another status, an unparseable body, `IOException`, `HttpTimeoutException`) → `Left("provider returned <status>: <first 200 chars>")` or `Left("cannot reach the provider: <exception class>")`.

`HttpPassthrough` posts the body unchanged to `<baseUrl>/<path>` (no trailing slash when `path` is empty) and returns `(status, body)`.

- [ ] **Step 1: Failing tests** using `com.sun.net.httpserver.HttpServer` on an ephemeral port (`HttpServer.create(InetSocketAddress(0), 0)`): one handler per scenario that records the request headers and body.
  - The request carries `Authorization: Bearer k` and `X-Clad-Interaction-Id: iid`; the JSON reply comes back.
  - 429 then 200 → `Right`, two requests, one sleep of `upstreamRetryWaitMillis` (pass a recording `sleep`).
  - 503 twice with `upstreamRetries = 1` → `Left` containing `503`.
  - A 200 with the body `<html>` → `Left`.
  - A handler that sleeps longer than the timeout. Use a test `Runtime` with `upstreamTimeoutSeconds = 1` and a handler sleeping 2 s → `Left` containing `cannot reach`.
  - Passthrough: a path, the body forwarded byte for byte, the status and body returned.
- [ ] **Step 2: Implement.** **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): upstream clients with interaction ids and configured retries`.

---

### Task 9: HTTP routes, authentication and the server

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/http/{Routes,Clients}.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/http/RoutesSpec.scala`

**Interfaces:**
- Consumes: `Gateway`, `GovernedRequest`, `GatewayResult` (7).
- Produces: `final case class Clients(byToken: Map[String, String], adminToken: String)` with `object Clients { def parse(spec: String, adminToken: String): Either[String, Clients] }`, where `spec` is `"<token>=<prefix>,<token>=<prefix>"`; `final class Routes(gateway: Gateway, clients: Clients) { def routes: org.http4s.HttpRoutes[cats.effect.IO] }`.

Routes (spec section 6):
- `POST /v1/chat/completions`: bearer token → prefix (unknown → 401). The body is parsed into `GovernedRequest`; a missing `clad` block or field → 400 `governance_rejected`. `clad.principal` must equal the prefix or start with `"<prefix>:"`, else 400 `governance_rejected` `principal_not_allowed_for_token`. The gateway runs inside `IO.blocking`. Results:

  | Result | Response |
  |---|---|
  | `Delivered` | 200: the response plus `"clad": {"interaction_id", "outcome"}`, header `X-Clad-Interaction-Id` |
  | `Blocked` | 403 `{"error": {"type": "governance_block", "interaction_id", "reason"}}`, header set |
  | `Rejected` | 400 `{"error": {"type": "governance_rejected", "reason"}}` |
  | `Unavailable` | 503 `{"error": {"type": "unavailable", "reason"}}`, header set when there is an id |

- `POST /v1/passthrough/<name>/<path…>`: the same token check; the template from `X-Clad-Template`, the principal from `X-Clad-Principal`, the session from `X-Clad-Session`. `Delivered` → the upstream's status and body plus `X-Clad-Interaction-Id`. Other results as above.
- `GET /v1/health` (no token): `{"status": "ok"}`.
- `POST /admin/contain` with `{project, reason}` and `POST /admin/release` with `{project}`: `Authorization: Bearer <adminToken>`, else 401.

- [ ] **Step 1: Failing tests** with `org.http4s.client.Client.fromHttpApp(routes.orNotFound)` and a `Gateway` built on `MemoryStore` and the helpers in `TestGateways.scala` (Task 7):
  - 200 with the `clad` block and header;
  - 403 for an unattested template, with `interaction_id`;
  - 400 for a system message;
  - 401 without a token;
  - 400 `principal_not_allowed_for_token` for `principal: "planner:u"` with the `api` token;
  - passthrough returns the upstream's status;
  - contain then call → 403 `contained`; release then call → no longer contained;
  - the admin route refuses a client token.
- [ ] **Step 2: Implement.** **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): HTTP routes with per-client principal prefixes`.

---

### Task 10: Bundle export and the conformance round trip

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/export/{BundleExport,CaddyLog}.scala`
- Test: `app/gateway-test/src/test/scala/clad/gateway/export/RoundTripSpec.scala`

**Interfaces:**
- Consumes: `PgStore`, `TestDatabase` (5, 4), `Gateway` (7), `Keys` (3), `Governance.bundleProjects` (1).
- Produces:

```scala
object CaddyLog { def read(path: java.nio.file.Path, listeners: Map[String, String]): Vector[ujson.Obj] }   // port -> provider host
object BundleExport:
  def write(out: java.nio.file.Path, since: java.time.Instant, until: java.time.Instant,
            probe: Option[(java.time.Instant, java.time.Instant)], readerUrl: String,
            egressLog: Option[java.nio.file.Path], listeners: Map[String, String], anchorDir: java.nio.file.Path,
            keysDir: java.nio.file.Path, governance: clad.gateway.config.Governance, app: String): Either[String, java.nio.file.Path]
```

`write` produces conformance spec section 4 exactly. Port the semantics of personal_finance's `src/governance/export/bundle.py` and `tables.py`:
- **`bundle.json`:** `bundleVersion` 1, `app`, `window`, optional `probeWindow`, `projects` = `governance.bundleProjects`, `keys` = `{"components": keyId, "supervisor": keyId}`, `exportedAt`, `exporter: "clad-gateway export 0.1.0"`.
- **The whole chain and GIL:** `audit.jsonl` holds the `line` column in `seq` order; `gil.jsonl` holds `{"interactionId", "registeredAt", "metadata": {...}}`, with the GIL columns overriding same-named metadata keys.
- **`degraded.jsonl`.**
- **`configs/<digest>.json`** with the bytes as stored, and **`artifacts/<digest>.json`**.
- **MDR files in the window:** `mdr_alerts.jsonl` and `mdr_heartbeats.jsonl`. They are empty until MDR exists, and that is correct.
- **`anchors.jsonl`:** one line `{"ts": updatedAt, "chainLength": count, "headDigest"}` from `ChainAnchorStore(anchorDir).read` when an anchor exists.
- **`keys/<keyId>.pub.pem`.**
- **`infra.jsonl`:** `CaddyLog.read` when the log exists.

Before `migrate` has run (`to_regclass('governance.audit_record')` is null), the database files are omitted: absent evidence is not the same as zero items. Instants are written with `Instant.toString`.

`CaddyLog.read` is personal_finance's `parse_egress_log`, ported: `ts` (epoch seconds) → Instant truncated to milliseconds, `request.host` port → provider host, `request.method`, `request.uri`, `status`, and the first `X-Clad-Interaction-Id` header value or `null`. No other header is copied.

- [ ] **Step 1: Failing test** (`RoundTripSpec.scala`). This is the main proof that the gateway produces what conformance verifies:

```scala
test("records written by the gateway export to a bundle that conformance verifies") {
  val db = TestDatabase.fresh()
  val keys = java.nio.file.Files.createTempDirectory("keys")
  Seq("components", "supervisor").foreach(r => clad.gateway.keys.Keys.generate(keys, r))
  val anchors = java.nio.file.Files.createTempDirectory("anchors")
  val cfg = Fixtures.approvedConfig()
  val comp = clad.gateway.keys.Keys.load(keys, "components").toOption.get
  val sup = clad.gateway.keys.Keys.load(keys, "supervisor").toOption.get
  val store = PgStore(db.writerUrl, cfg.runtime.chainLockKey, clad.audit.ChainAnchorStore(anchors), comp)
  val up = FakeUpstream()
  val gw = Gateway(cfg, Epg.build(cfg).toOption.get, store, comp, sup, up, NoPassthrough, java.nio.file.Files.createTempFile("c", ".json"), () => java.time.Instant.now())
  val start = java.time.Instant.now().minusSeconds(60)
  (1 to 3).foreach(_ => gw.complete(answerRequest()))          // agent.answer, attested and approved: delivered
  gw.complete(answerRequest(template = "agent.sql"))            // unattested: blocked at EPG
  // the egress log the proxy would have written, one line per upstream call
  val log = java.nio.file.Files.createTempFile("access", ".log")
  java.nio.file.Files.writeString(log, up.calls.map((_, iid) => ujson.write(ujson.Obj("ts" -> java.time.Instant.now().getEpochSecond.toDouble,
    "status" -> 200, "request" -> ujson.Obj("method" -> "POST", "uri" -> "/v1/openai/chat/completions", "host" -> "egress:8081",
    "headers" -> ujson.Obj("X-Clad-Interaction-Id" -> ujson.Arr(iid)))))).mkString("\n") + "\n")
  val out = java.nio.file.Files.createTempDirectory("bundle")
  BundleExport.write(out, start, java.time.Instant.now().plusSeconds(60), None, db.readerUrl, Some(log),
    Map("8081" -> "api.deepinfra.com"), anchors, keys, cfg.governance, "personal_finance").fold(fail(_), identity)
  val results = clad.conformance.BundleReader.read(out).map(b => clad.conformance.Conformance.evaluate(b, None, None)).fold(fail(_), identity)
  def verdict(id: String) = results.find(_.id == id).map(_.verdict.toString)
  for id <- Seq("R-AI2", "R-AI3", "R-AI4", "R-ANCHOR", "R-AI6", "R-EA1", "R-EA2", "R-AX5", "R-CFG-PARSE", "R-EPG-REEVAL", "R-EPG-BLOCK") do
    assert(verdict(id).contains("Satisfied"), s"$id: ${results.find(_.id == id)}")
}
```

(`answerRequest` is a helper in `TestGateways.scala` building a `GovernedRequest` for a template with one user message. `NoPassthrough` returns `Left("unused")`. If `Verdict`'s `toString` differs, compare with `clad.conformance.Verdict.Satisfied` directly.)

Add a second test: a database where `migrate` has not run gives a bundle with `infra.jsonl` and `bundle.json` but no `gil.jsonl` or `audit.jsonl`.

- [ ] **Step 2: Implement.** **Step 3: Run**, then `sbt test`. **Step 4: Commit** `feat(gateway): evidence bundle export verified by conformance`.

---

### Task 11: CLI, server, image and docs

**Files:**
- Create: `app/gateway/src/main/scala/clad/gateway/Main.scala`, `app/gateway/Dockerfile`, `tools/build-gateway-image.sh`, `app/gateway/README.md`
- Modify: `app/project/plugins.sbt` (add `addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.0")`), `app/build.sbt` (`gateway`: `assembly / assemblyJarName := "clad-gateway.jar"` and a merge strategy that discards `META-INF/*.SF|*.DSA|*.RSA` and concatenates `reference.conf`), `docs/architecture.md` (module graph and one paragraph)
- Test: `app/gateway-test/src/test/scala/clad/gateway/MainSpec.scala`

**Interfaces:**
- Consumes: everything above.
- Produces: the `clad-gateway` commands, all configured by environment variables:

| Command | Does | Needs |
|---|---|---|
| `serve` | load config, run `StartupCheck` (errors → exit 1; warnings printed `CLAD_GATEWAY_WARN …`), build the stores, keys and upstreams, serve on `CLAD_GATEWAY_PORT` | `CLAD_GATEWAY_CONFIG_DIR`, `CLAD_GATEWAY_KEYS_DIR`, `CLAD_GATEWAY_DATA_DIR` (anchors in `<data>/anchors`, containment at `<data>/containment.json`), `CLAD_GATEWAY_WRITER_URL`, `CLAD_GATEWAY_UPSTREAMS` (`name=url,…`; `openai` is the chat upstream), `CLAD_GATEWAY_KEY_<NAME>` per upstream, `CLAD_GATEWAY_TOKENS`, `CLAD_GATEWAY_ADMIN_TOKEN`, `CLAD_GATEWAY_PORT` |
| `migrate` | `Migrate.run` | `CLAD_GATEWAY_ADMIN_URL`, `CLAD_GATEWAY_WRITER_PASSWORD`, `CLAD_GATEWAY_READER_PASSWORD` |
| `keys` | `Keys.generate` for `components` and `supervisor`; prints a backup reminder | `CLAD_GATEWAY_KEYS_DIR` |
| `export --since … --until … --out … [--probe-since … --probe-until …]` | `BundleExport.write` | `CLAD_GATEWAY_READER_URL`, `CLAD_GATEWAY_EGRESS_LOG`, `CLAD_GATEWAY_LISTENERS` (`port=host,…`), `CLAD_GATEWAY_CONFIG_DIR`, `CLAD_GATEWAY_KEYS_DIR`, `CLAD_GATEWAY_DATA_DIR`, `CLAD_GATEWAY_APP` |
| `approval-digest` | prints `constraints_digest: <digest>` and `approval: pending (<why>)` or `approval: approved` | `CLAD_GATEWAY_CONFIG_DIR` |
| `check` | the startup check alone; exit 1 on errors | `CLAD_GATEWAY_CONFIG_DIR` |

A missing variable exits 2 with a message naming it. The CLI has no defaults for ports, URLs or numbers.

- [ ] **Step 1: Failing tests** (`MainSpec.scala`). Call `Main.run(args, env: Map[String, String]): Int`, which `main` wraps with `sys.env`:
  - `approval-digest` against the fixture config prints `pending`;
  - `check` returns 0;
  - `serve` without `CLAD_GATEWAY_PORT` returns 2 with the variable named;
  - `export` against a fresh test database writes `bundle.json`.
- [ ] **Step 2: Implement** `Main.scala`. `serve` uses `EmberServerBuilder` exactly as `api/GovernanceServer.scala` does.
- [ ] **Step 3: Image.**

`app/gateway/Dockerfile`:

```dockerfile
FROM docker.io/library/eclipse-temurin:17.0.12_7-jre
RUN useradd --create-home --uid 1000 clad
COPY clad-gateway.jar /opt/clad/clad-gateway.jar
RUN printf '#!/bin/sh\nexec java -jar /opt/clad/clad-gateway.jar "$@"\n' > /usr/local/bin/clad-gateway && chmod +x /usr/local/bin/clad-gateway
USER clad
ENTRYPOINT ["clad-gateway"]
CMD ["serve"]
```

`tools/build-gateway-image.sh`:

```bash
#!/usr/bin/env bash
# Build the clad-gateway jar and the localhost/clad-gateway:<version> image (podman).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
(cd "$ROOT/app" && sbt -batch gateway/assembly)
VERSION="$(cd "$ROOT/app" && sbt -batch -Dsbt.supershell=false 'print gateway/version' | tail -1)"
cp "$ROOT/app/gateway/target/scala-3.3.8/clad-gateway.jar" "$ROOT/app/gateway/clad-gateway.jar"
podman build -t "localhost/clad-gateway:$VERSION" -t localhost/clad-gateway:latest "$ROOT/app/gateway"
rm -f "$ROOT/app/gateway/clad-gateway.jar"
echo "built localhost/clad-gateway:$VERSION"
```

Add `app/gateway/clad-gateway.jar` to `.gitignore`.

- [ ] **Step 4: Docs.** `app/gateway/README.md`, in under a page: what the gateway does, the adopter's files (spec section 5), the commands table above, the HTTP API (spec section 6), and that ROC and MDR are not in this version. Add `gateway` (depends on core, evaluation, runtime, output, audit, integrity, config, api) to the module graph in `docs/architecture.md`, with one paragraph.
- [ ] **Step 5: Run** `sbt test` (with `CLAD_TEST_PG_ADMIN_URL`), then `bash tools/build-gateway-image.sh` and `podman run --rm localhost/clad-gateway:latest approval-digest` (expected: exit 2 naming `CLAD_GATEWAY_CONFIG_DIR`). Then run `cicd/llm/preflight.sh $(git diff --name-only HEAD~10...HEAD)` with the range covering this plan's commits, and update `completion.json` (task `clad-gateway`, one check per command, `not_verified` listing ROC, MDR and CI).
- [ ] **Step 6: Commit** `feat(gateway): CLI, server, container image and docs`.

---

### Task 12: personal_finance: run clad-gateway in the pod

**Files (personal_finance worktree):**
- Modify: `docker/podman-compose.yml`, `.env.example`, `scripts/install.sh`, `config/governance/constraints.yaml` (add `name: personal_finance` as the first key), `config/governance/runtime.yaml` (add `upstream_timeout_seconds: 60  # the former LlmClient timeout` and `upstream_retry_wait_ms: 1000  # the former LlmClient retry wait`)
- Test: `stack/tests/test_compose.py` (add tests)

**Interfaces:**
- Produces: compose service `clad-gateway` on network `internal` only, port 8090 inside the pod, image `localhost/clad-gateway:latest`. `api` and `planner` reach it as `http://clad-gateway:8090/v1`.

- [ ] **Step 1: Failing tests** in `stack/tests/test_compose.py`:

```python
def test_clad_gateway_holds_the_provider_and_signing_keys_and_the_api_does_not():
    services = _compose()["services"]
    gw = services["clad-gateway"]
    assert gw["networks"] == ["internal"]
    env = gw["environment"]
    assert "CLAD_GATEWAY_KEY_OPENAI" in env and "CLAD_GATEWAY_KEY_JEV" in env
    assert any("secrets/governance" in v for v in gw["volumes"])
    api_env = services["api"]["environment"]
    assert "DEEPINFRA_API_KEY" not in api_env and "TYPESAFE_API_KEY" not in api_env and "GOV_WRITER_URL" not in api_env
    assert not any("secrets/governance" in str(v) for v in services["api"].get("volumes", []))


def test_only_clad_gateway_holds_model_keys():
    for name, svc in _compose()["services"].items():
        keys = svc.get("environment", {})
        keys = keys.keys() if isinstance(keys, dict) else [e.split("=")[0] for e in keys]
        if name != "clad-gateway":
            assert not any(k in keys for k in ("DEEPINFRA_API_KEY", "TYPESAFE_API_KEY", "CLAD_GATEWAY_KEY_OPENAI")), name
```

Remove the earlier `test_only_api_holds_model_keys` and `test_only_api_mounts_the_signing_keys`; this task changes the behaviour they pinned.

- [ ] **Step 2: Compose.** Add the service:

```yaml
  # CLAD governance gateway (clad repo, app/gateway): the only holder of provider and signing keys
  clad-gateway:
    image: localhost/clad-gateway:latest
    networks: [internal]
    depends_on:
      postgres:
        condition: service_healthy
      egress:
        condition: service_started
    environment:
      CLAD_GATEWAY_CONFIG_DIR: /config
      CLAD_GATEWAY_KEYS_DIR: /keys
      CLAD_GATEWAY_DATA_DIR: /data
      CLAD_GATEWAY_PORT: "8090"
      CLAD_GATEWAY_APP: personal_finance
      CLAD_GATEWAY_ADMIN_URL: jdbc:postgresql://postgres:5432/pf?user=pf&password=${PG_PASSWORD}
      CLAD_GATEWAY_WRITER_PASSWORD: ${GOV_WRITER_PASSWORD}
      CLAD_GATEWAY_READER_PASSWORD: ${GOV_READER_PASSWORD}
      CLAD_GATEWAY_WRITER_URL: jdbc:postgresql://postgres:5432/pf?user=clad_gov_writer&password=${GOV_WRITER_PASSWORD}
      CLAD_GATEWAY_READER_URL: jdbc:postgresql://postgres:5432/pf?user=clad_gov_reader&password=${GOV_READER_PASSWORD}
      CLAD_GATEWAY_UPSTREAMS: openai=http://egress:8081/v1/openai,jev=http://egress:8082/v1/systemone
      CLAD_GATEWAY_KEY_OPENAI: ${DEEPINFRA_API_KEY:-}
      CLAD_GATEWAY_KEY_JEV: ${TYPESAFE_API_KEY:-}
      CLAD_GATEWAY_TOKENS: ${PF_API_TOKEN}=api,${PLANNER_GATEWAY_TOKEN}=planner
      CLAD_GATEWAY_ADMIN_TOKEN: ${CLAD_ADMIN_TOKEN}
      CLAD_GATEWAY_EGRESS_LOG: /egress/access.log
      CLAD_GATEWAY_LISTENERS: 8081=api.deepinfra.com,8082=api.typesafe.ai
    volumes:
      - ../config/governance:/config:ro,z
      - ../secrets/governance:/keys:ro,z
      - ../data/governance:/data:z
      - ../data/egress:/egress:ro,z
```

Then:
- In `api`, remove `DEEPINFRA_API_KEY`, `TYPESAFE_API_KEY`, `LLM_BASE_URL`, `JEV_URL`, `GOV_WRITER_URL`, `GOV_READER_URL`, `GOV_WRITER_PASSWORD` and `GOV_READER_PASSWORD`, and the `secrets/governance` mount. Add `CLAD_GATEWAY_URL: http://clad-gateway:8090/v1` and `depends_on: clad-gateway`.
- In `planner`, set `LLM_BASE_URL: http://clad-gateway:8090/v1` and `PLANNER_GATEWAY_TOKEN: ${PLANNER_GATEWAY_TOKEN}`, with `depends_on: clad-gateway`.
- `.env.example` and `install.sh`: generate `PLANNER_GATEWAY_TOKEN` and `CLAD_ADMIN_TOKEN` (`openssl rand -hex 32`) like `PF_API_TOKEN`. After the pod is healthy, run `podman exec pf_clad-gateway_1 clad-gateway migrate` (it replaces `pf-stack governance migrate`).
- [ ] **Step 3: Run** `cd stack && uv run pytest tests/test_compose.py -q`. Expected: pass.
- [ ] **Step 4: Commit** `feat(governance): run clad-gateway in the pod; only it holds provider and signing keys`.
- [ ] **Step 5 (OPERATOR):**
  1. `bash /Users/david/workspace/clad-conformance/tools/build-gateway-image.sh`.
  2. Add the two new tokens to the worktree `.env`.
  3. Recreate `clad-gateway`, `api` and `planner`.
  4. `podman exec pf_clad-gateway_1 clad-gateway migrate`, then `podman exec pf_clad-gateway_1 clad-gateway check` (expected: exit 0, with warnings for approvals and attestations).

  This step does not switch the call sites yet: until Task 13 lands, the stack's calls fail with "connection refused" to its old in-app gateway. Do Step 5 and Task 13 together.

---

### Task 13: personal_finance: call sites go to clad-gateway

**Files (personal_finance worktree):**
- Create: `src/governance/gateway/client.py` (`ClientGateway`: HTTP to clad-gateway)
- Modify: `src/governance/gateway/factory.py`, `src/governance/gateway/adapters.py`, `stack/pf_stack/pipelines/steps.py` (principal `api:pipeline`), `stack/pf_stack/settings.py` (`clad_gateway_url`), `apps/planner/server/agent/llm.ts` (`GOVERNED_HOSTS` = `{"clad-gateway"}`), `apps/planner/server/server.ts` (base URL default `http://clad-gateway:8090/v1`, `apiKey: process.env.PLANNER_GATEWAY_TOKEN`)
- Test: `stack/tests/governance/test_client.py`, `stack/tests/governance/test_adapters.py` (rewrite against a fake `ClientGateway`), `apps/planner/tests/unit/llm.test.ts`

**Interfaces:**
- Produces:

```python
class ClientGateway:
    def __init__(self, base_url: str, token: str, http: httpx.Client | None = None)
    def complete(self, req: GovernedRequest) -> GovernedResponse     # POST {base}/chat/completions with the clad block
    def jev(self, principal: str, session: str | None, state: dict, questions: dict) -> dict
def build_gateway(settings) -> ClientGateway                        # factory: CLAD_GATEWAY_URL + PF_API_TOKEN
```

`complete` maps 403 `governance_block` → `GovernanceBlocked(interaction_id, reason)`, 400 → `GovernanceRejected(reason)`, 503 or a network error → `LlmUnavailable(reason)`, and returns `GovernedResponse(interaction_id, outcome, choices[0].message, body)`. `jev` builds `JevClient(Settings(jev_url=f"{base}/passthrough/jev", typesafe_api_key=token, ...))`. This is the only `JevClient` construction, so the chokepoint test still holds; it calls `evaluate(state, questions, headers={"X-Clad-Template": "router.jev", "X-Clad-Principal": principal, "X-Clad-Session": session or ""})`. A 403 from the passthrough surfaces as `JevUnavailable`, so routing falls back.

- [ ] **Step 1: Failing tests** (`test_client.py`, respx on `http://clad-gateway:8090/v1`):
  - the body has `clad` with `template_id`, `vars`, `principal`, `session`, `evidence`, `model_tier`, and no system message; `Authorization: Bearer <token>`;
  - 200 → `GovernedResponse` with the interaction id from the `clad` block;
  - 403 → `GovernanceBlocked` with `interaction_id`;
  - 400 → `GovernanceRejected`;
  - 503 → `LlmUnavailable`;
  - `jev` posts to `/passthrough/jev` with the three `X-Clad-*` headers.

  In `llm.test.ts`, the accepted host is now `clad-gateway`, and `api` is rejected.
- [ ] **Step 2: Implement.** In `adapters.py`, `GovernedLlm` and `GovernedJev` keep their signatures and call `ClientGateway`. Add a `router.jev.yaml` template (project `router`, `model_tier: small`, `response_format: text`, `evidence_format: none`, no slots, text `Jev routing (provider-owned prompt).` followed by the data line) so the passthrough's template check passes. EPG doesn't run for passthrough.
- [ ] **Step 3: Run** `cd stack && uv run pytest -q` and `cd apps/planner && npm test && npm run typecheck`. Expected: all pass; the chokepoint test passes.
- [ ] **Step 4: Commit** `feat(governance): stack and planner call clad-gateway`.
- [ ] **Step 5 (OPERATOR):**
  1. Rebuild `api` and `planner`, and recreate `api`, `feast-server`, `planner` and `caddy`.
  2. Ask one question with `/api/v1/ask/sync` and send one planner chat turn.

  Expected: with the templates unattested and the models pending, both are refused with "withheld by policy". `governance.audit_record` gains `epg` records with outcome `block`, and the GIL gains `router`, `agent` and `planner-chat` entries. Once David has attested and approved, the same calls are delivered.

---

### Task 14: personal_finance: delete the Python core and remeasure

**Files (personal_finance worktree):**
- Delete: `src/governance/{records.py,ids.py,canonical_time.py,signing.py,supervisor.py,templates.py,runtime_config.py}`, `src/governance/store/`, `src/governance/epg/`, `src/governance/config/`, `src/governance/export/`, `src/governance/gateway/{core.py,http.py,models.py}`. Move `GovernedRequest` and `GovernedResponse` into `client.py`.
- Delete tests: `stack/tests/governance/{test_records,test_signing,test_store,test_supervisor,test_gateway_core,test_epg,test_config_loader,test_templates,test_export,test_runtime_config,test_migrate_sql,test_http,test_factory,test_test_database}.py`, `golden/`, `stack/tests/integration/{test_governance_worm,test_governance_chain,gov_test_db}.py` and the `gov_db` fixture.
- Modify: `stack/pf_stack/cli.py` (remove the `governance` command group), `stack/pf_stack/api/app.py` (remove `governed_router`), `scripts/governance_measure.sh` (export with `podman exec pf_clad-gateway_1 clad-gateway export --since … --until … --out /data/bundles/<stamp>-<label> …`; bundles then land in `data/governance/bundles/`), `scripts/install.sh` (remove `pf-stack governance keys`; run `podman run --rm -v "$ROOT/secrets/governance:/keys:z" -e CLAD_GATEWAY_KEYS_DIR=/keys localhost/clad-gateway:latest keys`), `README.md` (the governance section points to the clad gateway README)

- [ ] **Step 1:** Delete the files. Run `cd stack && uv run pytest -q`. Expected: the remaining suite passes (adapters, client, chokepoint, compose). A failure means something still imports a deleted module: fix the import, never restore the module.
- [ ] **Step 2:** Run `git grep -n "src.governance\.\(records\|signing\|store\|epg\|config\|export\|supervisor\|templates\)"`. Expected: no output.
- [ ] **Step 3: Commit** `refactor(governance): the enforcement core now lives in clad-gateway`.
- [ ] **Step 4 (OPERATOR):**
  1. Rebuild `api`, recreate it, and run `scripts/verify.sh`. Expected: everything but the Advisor answer passes; that check reports "withheld by policy" until attestation.
  2. Run `scripts/governance_measure.sh clad-gateway`.
  3. Commit `docs/governance/scorecards/04-clad-gateway.{md,json}`.

  Expected, before David's approvals:
  - R-EA1, R-EA2, R-AI2, R-AI3, R-AI4, R-AI6, R-ANCHOR, R-AX5, R-CFG-PARSE and R-EPG-REEVAL are SATISFIED. That matches or beats `03-gateway-planner`.
  - R-EPG-BLOCK is SATISFIED: everything is blocked at EPG, so the model is never called.
  - R-EA4 is VIOLATED until David approves: the approval is missing.
  - Router calls carry no EPG record by design.

  Record any requirement that is worse than in `03-gateway-planner` in `docs/governance/decisions/2026-10-16-gateway-moves-to-clad.md` with its cause.

  Also report the gateway's overhead: the median and the maximum of `overhead_ms` from `podman logs pf_clad-gateway_1 | grep CLAD_GATEWAY_CALL` over the workload window. Write them in the decision note; they decide whether a latency budget belongs in `runtime.yaml` for the ROC plan.
- [ ] **Step 5:** Update the decision note's "What this means here" with the commit range and the scorecard result. **Commit** `docs(governance): first scorecard from clad-gateway`.
