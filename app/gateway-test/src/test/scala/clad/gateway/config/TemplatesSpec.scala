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

  private def promptsDir(slotsLine: String, text: String): Path =
    val d = java.nio.file.Files.createTempDirectory("prompts")
    val project = Fixtures.config.governance.projects.keySet.toVector.sorted.headOption.getOrElse("p")
    val tier = t.models.tiers.keySet.toVector.sorted.headOption.getOrElse("t")
    java.nio.file.Files.writeString(d.resolve("x.t.yaml"),
      s"id: x.t\nversion: 1\nproject: $project\nmodel_tier: $tier\nresponse_format: text\nevidence_format: none\n$slotsLine\ntext: \"$text\"\n")
    d

  test("load refuses text slots that are not declared, naming the file") {
    val r = Templates.load(promptsDir("slots: {}", "hello {{x}}"), t.models, Fixtures.config.governance)
    assert(r.left.exists(e => e.contains("x.t.yaml") && e.contains("do not match")), r.toString)
  }

  test("load refuses a slots field that is not a mapping, naming the file") {
    val r = Templates.load(promptsDir("slots: [a]", "hello"), t.models, Fixtures.config.governance)
    assert(r.left.exists(e => e.contains("x.t.yaml") && e.contains("mapping")), r.toString)
  }

  test("render returns a Left for a hand-built template using an undeclared slot") {
    val x = Template("h", "1", "p", "Standard", "m", "t", "hi {{x}}", "text", "none", Map.empty)
    assert(Templates.render(x, Map.empty).left.exists(_.contains("undeclared")))
  }
