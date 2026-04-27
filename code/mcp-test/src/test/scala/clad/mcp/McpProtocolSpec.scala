package clad.mcp

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}

class McpProtocolSpec extends AnyFlatSpec with Matchers:

  "parseMessage" should "parse JSON-RPC request with id and method" in {
    val json = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"foo":"bar"}}"""
    val result = McpProtocol.parseMessage(json)

    result shouldBe defined
    result.get shouldBe a[JsonRpcRequest]
    val req = result.get.asInstanceOf[JsonRpcRequest]
    req.jsonrpc shouldBe "2.0"
    req.id shouldBe ujson.Num(1)
    req.method shouldBe "initialize"
    req.params shouldBe ujson.Obj("foo" -> "bar")
  }

  it should "parse notification without id" in {
    val json = """{"jsonrpc":"2.0","method":"notifications/initialized"}"""
    val result = McpProtocol.parseMessage(json)

    result shouldBe defined
    result.get shouldBe a[JsonRpcNotification]
    val notif = result.get.asInstanceOf[JsonRpcNotification]
    notif.jsonrpc shouldBe "2.0"
    notif.method shouldBe "notifications/initialized"
  }

  it should "parse tools/call with params" in {
    val json = """{"jsonrpc":"2.0","id":"req-1","method":"tools/call","params":{"name":"check_governance","arguments":{}}}"""
    val result = McpProtocol.parseMessage(json)

    result shouldBe defined
    result.get shouldBe a[JsonRpcRequest]
    val req = result.get.asInstanceOf[JsonRpcRequest]
    req.method shouldBe "tools/call"
    req.params.obj.get("name").map(_.str) shouldBe Some("check_governance")
  }

  it should "return None for invalid JSON" in {
    val invalid = """{"jsonrpc":"2.0",invalid}"""
    val result = McpProtocol.parseMessage(invalid)
    result shouldBe None
  }

  it should "return None for empty string" in {
    val result = McpProtocol.parseMessage("")
    result shouldBe None
  }

  "serializeResponse" should "produce valid JSON with jsonrpc, id, and result" in {
    val response = JsonRpcResponse("2.0", ujson.Num(1), ujson.Obj("status" -> "ok"))
    val serialized = McpProtocol.serializeResponse(response)

    val parsed = ujson.read(serialized)
    parsed("jsonrpc").str shouldBe "2.0"
    parsed("id").num shouldBe 1
    parsed("result").obj("status").str shouldBe "ok"
  }

  "serializeError" should "produce JSON with error code and message" in {
    val error = JsonRpcError("2.0", ujson.Num(1), ErrorObject(-32600, "Invalid Request"))
    val serialized = McpProtocol.serializeError(error)

    val parsed = ujson.read(serialized)
    parsed("jsonrpc").str shouldBe "2.0"
    parsed("id").num shouldBe 1
    parsed("error").obj("code").num shouldBe -32600
    parsed("error").obj("message").str shouldBe "Invalid Request"
  }

  "initializeResultJson" should "include protocolVersion, capabilities.tools, and serverInfo" in {
    val result = McpProtocol.initializeResultJson()

    result.obj("protocolVersion").str shouldBe "2024-11-05"
    result.obj("capabilities").obj("tools").obj("listChanged").bool shouldBe false
    result.obj("serverInfo").obj("name").str shouldBe "clad-governance"
    result.obj("serverInfo").obj("version").str shouldBe "0.1.0"
  }

  "toolResultJson" should "serialize content blocks with type and text" in {
    val toolResult = McpProtocol.ToolResult(
      content = Seq(
        McpProtocol.ContentBlock("text", "Governance check passed"),
        McpProtocol.ContentBlock("text", "No violations found")
      )
    )
    val json = McpProtocol.toolResultJson(toolResult)

    json.obj("content").arr.length shouldBe 2
    json.obj("content").arr(0).obj("type").str shouldBe "text"
    json.obj("content").arr(0).obj("text").str shouldBe "Governance check passed"
    json.obj("content").arr(1).obj("text").str shouldBe "No violations found"
  }

  it should "serialize isError flag" in {
    val toolResult = McpProtocol.ToolResult(
      content = Seq(McpProtocol.ContentBlock("text", "Error occurred")),
      isError = true
    )
    val json = McpProtocol.toolResultJson(toolResult)

    json.obj("isError").bool shouldBe true
  }

  "JsonRpcTransport" should "read message from input stream" in {
    val input = """{"jsonrpc":"2.0","id":1,"method":"test"}""" + "\n"
    val inputStream = ByteArrayInputStream(input.getBytes)
    val transport = JsonRpcTransport(input = inputStream)

    val message = transport.readMessage()
    message shouldBe defined
    message.get shouldBe a[JsonRpcRequest]
  }

  it should "write response to output stream" in {
    val outputStream = ByteArrayOutputStream()
    val printStream = PrintStream(outputStream)
    val transport = JsonRpcTransport(
      input = ByteArrayInputStream(Array.empty),
      responseStream = printStream
    )

    val response = JsonRpcResponse("2.0", ujson.Num(1), ujson.Obj("status" -> "ok"))
    transport.writeResponse(response)

    val output = outputStream.toString.trim
    val parsed = ujson.read(output)
    parsed("jsonrpc").str shouldBe "2.0"
    parsed("id").num shouldBe 1
  }

  it should "return None when stdin is closed (empty stream)" in {
    val emptyStream = ByteArrayInputStream(Array.empty)
    val transport = JsonRpcTransport(input = emptyStream)

    val message = transport.readMessage()
    message shouldBe None
  }

  it should "write to stderr stream via log" in {
    val logStream = ByteArrayOutputStream()
    val printStream = PrintStream(logStream)
    val transport = JsonRpcTransport(
      input = ByteArrayInputStream(Array.empty),
      logStream = printStream
    )

    transport.log("Test log message")

    val output = logStream.toString.trim
    output shouldBe "Test log message"
  }
