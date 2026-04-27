package clad.mcp

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.config.*
import clad.config.test.SampleConfigs

class McpDispatcherSpec extends AnyFlatSpec with Matchers:

  def mkDispatcher(): McpDispatcher =
    val Right(config) = ConfigLoader.loadFromString(SampleConfigs.sampleJson): @unchecked
    val Right(engine) = ConfigLoader.buildEngine(config): @unchecked
    McpDispatcher(engine, config)

  "McpDispatcher" should "handle initialize request" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Num(1), "initialize", ujson.Obj())

    val response = dispatcher.dispatch(request)
    response shouldBe a[JsonRpcResponse]

    val resp = response.asInstanceOf[JsonRpcResponse]
    resp.jsonrpc shouldBe "2.0"
    resp.id shouldBe ujson.Num(1)

    val result = resp.result
    result("protocolVersion").str shouldBe "2024-11-05"
    result("capabilities")("tools").obj should not be empty
    result("serverInfo")("name").str shouldBe "clad-governance"
  }

  it should "handle tools/list request" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Num(2), "tools/list", ujson.Obj())

    val response = dispatcher.dispatch(request)
    response shouldBe a[JsonRpcResponse]

    val resp = response.asInstanceOf[JsonRpcResponse]
    val tools = resp.result("tools").arr
    tools should have size 6
  }

  it should "include evaluate_prompt in tools/list" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Num(3), "tools/list", ujson.Obj())

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    val toolNames = resp.result("tools").arr.map(_("name").str)

    toolNames should contain("evaluate_prompt")
  }

  it should "provide input schemas in tools/list" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Num(4), "tools/list", ujson.Obj())

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    val evaluateTool = resp.result("tools").arr.find(_("name").str == "evaluate_prompt").get

    evaluateTool("inputSchema")("type").str shouldBe "object"
    evaluateTool("inputSchema")("required").arr should contain(ujson.Str("prompt"))
  }

  it should "handle tools/call for evaluate_prompt" in {
    val dispatcher = mkDispatcher()
    val params = ujson.Obj(
      "name" -> "evaluate_prompt",
      "arguments" -> ujson.Obj(
        "prompt" -> "test",
        "metadata" -> ujson.Obj("audit_logging" -> "enabled")
      )
    )
    val request = JsonRpcRequest("2.0", ujson.Num(5), "tools/call", params)

    val response = dispatcher.dispatch(request)
    response shouldBe a[JsonRpcResponse]

    val resp = response.asInstanceOf[JsonRpcResponse]
    val result = resp.result
    result("isError").bool shouldBe false
    result("content").arr(0)("type").str shouldBe "text"
    result("content").arr(0)("text").str should include("Evaluation Status:")
  }

  it should "handle tools/call for list_constraints" in {
    val dispatcher = mkDispatcher()
    val params = ujson.Obj(
      "name" -> "list_constraints",
      "arguments" -> ujson.Obj()
    )
    val request = JsonRpcRequest("2.0", ujson.Num(6), "tools/call", params)

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    val result = resp.result
    result("content").arr(0)("text").str should include("Active Governance Constraints")
  }

  it should "handle tools/call for get_governance_config" in {
    val dispatcher = mkDispatcher()
    val params = ujson.Obj(
      "name" -> "get_governance_config",
      "arguments" -> ujson.Obj()
    )
    val request = JsonRpcRequest("2.0", ujson.Num(7), "tools/call", params)

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    val result = resp.result
    result("content").arr(0)("text").str should include("Governance Configuration:")
  }

  it should "return error for unknown method" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Num(8), "unknown/method", ujson.Obj())

    val response = dispatcher.dispatch(request)
    response shouldBe a[JsonRpcError]

    val err = response.asInstanceOf[JsonRpcError]
    err.error.code shouldBe -32601
  }

  it should "handle unknown tool in tools/call" in {
    val dispatcher = mkDispatcher()
    val params = ujson.Obj(
      "name" -> "nonexistent_tool",
      "arguments" -> ujson.Obj()
    )
    val request = JsonRpcRequest("2.0", ujson.Num(9), "tools/call", params)

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    val result = resp.result
    result("isError").bool shouldBe true
    result("content").arr(0)("text").str should include("Unknown tool")
  }

  it should "preserve numeric request ID" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Num(42), "initialize", ujson.Obj())

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    resp.id shouldBe ujson.Num(42)
  }

  it should "preserve string request ID" in {
    val dispatcher = mkDispatcher()
    val request = JsonRpcRequest("2.0", ujson.Str("req-abc"), "initialize", ujson.Obj())

    val response = dispatcher.dispatch(request)
    val resp = response.asInstanceOf[JsonRpcResponse]
    resp.id shouldBe ujson.Str("req-abc")
  }
