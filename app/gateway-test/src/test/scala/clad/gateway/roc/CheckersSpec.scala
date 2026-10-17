package clad.gateway.roc

import clad.gateway.config.*
import org.scalatest.funsuite.AnyFunSuite

class CheckersSpec extends AnyFunSuite:
  private val gov: Governance = Governance.load(Fixtures.dir.resolve("constraints.yaml")).fold(e => fail(e), identity)
  private val roc: RocConfig = RocConfig.load(Fixtures.dir, gov).fold(e => fail(e), identity)
  private val rt: Runtime = Fixtures.runtime

  private def ctx(content: String = "", toolCalls: Vector[ujson.Obj] = Vector.empty, evidence: Option[ujson.Value] = None,
                  tools: Vector[String] = Vector.empty, schema: Option[ujson.Value] = None): OutputContext =
    OutputContext(content, toolCalls, evidence, tools, schema, "agent.answer")

  private def run(p: String, c: OutputContext): Boolean = Checkers.check(p, c, rt, roc, gov).fold(e => fail(e), identity)
  private def rows(js: String): Option[ujson.Value] = Some(ujson.read(js))
  private def call(name: String, args: ujson.Value): ujson.Obj =
    ujson.Obj("function" -> ujson.Obj("name" -> name, "arguments" -> args))

  test("numbers_grounded: percentages match the ratio, rounded to the decimals shown") {
    val ev = rows("""[{"tax_drag_ratio": 0.0666328857, "other": 0.0761007292}]""")
    assert(run("numbers_grounded", ctx("The drag is 6.66 % a year.", evidence = ev)))
    assert(run("numbers_grounded", ctx("It reaches 7.61% later.", evidence = ev)))
    assert(!run("numbers_grounded", ctx("It reaches 7.62% later.", evidence = ev)))
  }

  test("numbers_grounded: currency and thousands separators are stripped") {
    assert(run("numbers_grounded", ctx("Balance $1,234.50 today.", evidence = rows("""[{"balance": 1234.5}]"""))))
    assert(!run("numbers_grounded", ctx("Balance $1,234.60 today.", evidence = rows("""[{"balance": 1234.5}]"""))))
  }

  test("numbers_grounded: an absent number is ungrounded, a small integer is ignored") {
    val ev = rows("""[{"v": 1234.5}]""")
    assert(!run("numbers_grounded", ctx("There are 42 items.", evidence = ev)))
    assert(run("numbers_grounded", ctx("Pick 3 of them.", evidence = ev)))
  }

  test("numbers_grounded: a number only in the question is ungrounded; no evidence is not applicable and satisfied") {
    val c = ctx("You asked about 5000 dollars.", evidence = rows("""[{"v": 1}]"""))
    assert(!run("numbers_grounded", c))
    val none = ctx("You asked about 5000 dollars.")
    assert(!Checkers.applicable("numbers_grounded", none) && Checkers.applicable("numbers_grounded", c))
    assert(run("numbers_grounded", none))
  }

  test("extraction_grounded: numbers in the extracted JSON must appear in the page text") {
    val pages = Some(ujson.Arr("Form 1099-INT Box 1 interest income 1,234.50 federal withheld 100"))
    assert(run("extraction_grounded", ctx("""{"interest": 1234.5, "withheld": 100}""", evidence = pages)))
    assert(!run("extraction_grounded", ctx("""{"interest": 9999.5}""", evidence = pages)))
    assert(!run("extraction_grounded", ctx("not json", evidence = pages)))
    assert(run("extraction_grounded", ctx("""{"interest": 9999.5}""")))
  }

  test("schema_valid: content must be JSON that validates against the request schema") {
    val schema = Some(ujson.read("""{"type":"object","required":["a"],"properties":{"a":{"type":"integer"}}}"""))
    assert(run("schema_valid", ctx("""{"a": 1}""", schema = schema)))
    assert(!run("schema_valid", ctx("""{"a": "x"}""", schema = schema)))
    assert(!run("schema_valid", ctx("""{"b": 1}""", schema = schema)))
    assert(!run("schema_valid", ctx("plain words", schema = schema)))
    assert(!Checkers.applicable("schema_valid", ctx("{}")) && run("schema_valid", ctx("plain words")))
  }

  test("tool_calls_valid: names must be in the request and arguments a JSON object") {
    val tools = Vector("get_metric")
    assert(run("tool_calls_valid", ctx(toolCalls = Vector(call("get_metric", ujson.Str("""{"x": 1}"""))), tools = tools)))
    assert(!run("tool_calls_valid", ctx(toolCalls = Vector(call("apply_change_set", ujson.Str("{}"))), tools = tools)))
    assert(!run("tool_calls_valid", ctx(toolCalls = Vector(call("get_metric", ujson.Str("[1]"))), tools = tools)))
    assert(!run("tool_calls_valid", ctx(toolCalls = Vector(call("get_metric", ujson.Str("not json"))), tools = tools)))
    assert(run("tool_calls_valid", ctx(tools = tools)))
  }

  test("contains_pii reuses the governance patterns") {
    assert(run("contains_pii", ctx("My SSN is 123-45-6789.")))
    assert(run("contains_pii", ctx("EIN 12-3456789 on file")))
    assert(!run("contains_pii", ctx("Nothing sensitive here, just 42.")))
  }

  test("guarantee_language is found case-insensitively") {
    assert(run("guarantee_language", ctx("These are Guaranteed Returns.")))
    assert(run("guarantee_language", ctx("A risk-free option")))
    assert(run("guarantee_language", ctx("you can't lose")))
    assert(run("guarantee_language", ctx("There is NO RISK at all")))
    assert(!run("guarantee_language", ctx("Returns vary and risk is real.")))
  }

  test("route_valid: the intent choice must be one the router knows") {
    val known = roc.routerIntents.toSeq.sorted.headOption.getOrElse(fail("no intents"))
    def reply(i: String) = s"""{"answers": {"intent": {"choice": "$i"}}}"""
    assert(run("route_valid", ctx(reply(known))))
    assert(!run("route_valid", ctx(reply("other"))))
    assert(!run("route_valid", ctx("""{"answers": {}}""")))
    assert(!run("route_valid", ctx("garbage")))
  }

  test("signs: a leading minus (before or after $) is part of the figure") {
    assert(run("numbers_grounded", ctx("a loss of -$1,234.50 this year", evidence = rows("""[{"pnl": -1234.5}]"""))))
    assert(run("numbers_grounded", ctx("a loss of $-1,234.50 this year", evidence = rows("""[{"pnl": -1234.5}]"""))))
    assert(!run("numbers_grounded", ctx("a loss of -$1,234.50 this year", evidence = rows("""[{"pnl": 1234.5}]"""))))
    assert(run("numbers_grounded", ctx("down -5.5%", evidence = rows("""[{"chg": -0.055}]"""))))
    assert(!run("numbers_grounded", ctx("down 5.5%", evidence = rows("""[{"chg": -0.055}]"""))))
  }

  test("extraction_grounded: -50 matches page text -50.00 and accounting (50.00)") {
    assert(run("extraction_grounded", ctx("""{"adj": -50}""", evidence = Some(ujson.Arr("adjustment -50.00 total")))))
    assert(run("extraction_grounded", ctx("""{"adj": -50}""", evidence = Some(ujson.Arr("adjustment (50.00) total")))))
    assert(run("extraction_grounded", ctx("""{"adj": -50}""", evidence = Some(ujson.Arr("adjustment ($50.00) total")))))
    assert(!run("extraction_grounded", ctx("""{"adj": 50}""", evidence = Some(ujson.Arr("adjustment (50.00) total")))))
  }

  test("identifiers and ISO dates are not figures; a standalone year is") {
    val ev = rows("""[{"v": 1.5}]""")
    assert(run("numbers_grounded", ctx("Your 401k plan", evidence = ev)))
    assert(run("numbers_grounded", ctx("The S6 model", evidence = ev)))
    assert(run("numbers_grounded", ctx("Form 1099-INT and W-2 filed", evidence = ev)))
    assert(run("numbers_grounded", ctx("Valid on 2026-10-17", evidence = ev)))
    assert(!run("numbers_grounded", ctx("You retire in 2045", evidence = ev)))
    assert(run("numbers_grounded", ctx("You retire in 2045", evidence = rows("""[{"year": 2045}]"""))))
    assert(run("numbers_grounded", ctx("The balance was 1234.50.", evidence = rows("""[{"b": 1234.5}]"""))))
  }

  test("an unknown property is an error") {
    assert(Checkers.check("nope", ctx(), rt, roc, gov).isLeft)
  }
