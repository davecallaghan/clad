package clad.mcp

import upickle.default.*

// JSON-RPC message types
sealed trait JsonRpcMessage

case class JsonRpcRequest(jsonrpc: String, id: ujson.Value, method: String, params: ujson.Value = ujson.Obj()) extends JsonRpcMessage
case class JsonRpcNotification(jsonrpc: String, method: String, params: ujson.Value = ujson.Obj()) extends JsonRpcMessage
case class JsonRpcResponse(jsonrpc: String, id: ujson.Value, result: ujson.Value) extends JsonRpcMessage
case class ErrorObject(code: Int, message: String)
case class JsonRpcError(jsonrpc: String, id: ujson.Value, error: ErrorObject) extends JsonRpcMessage

// MCP protocol types
object McpProtocol:
  val ProtocolVersion = "2024-11-05"

  case class ServerInfo(name: String = "clad-governance", version: String = "0.1.0")
  case class ServerCapabilities(tools: ujson.Value = ujson.Obj("listChanged" -> false))
  case class InitializeResult(
    protocolVersion: String = ProtocolVersion,
    capabilities: ServerCapabilities = ServerCapabilities(),
    serverInfo: ServerInfo = ServerInfo()
  )
  case class ToolDefinition(name: String, description: String, inputSchema: ujson.Value)
  case class ContentBlock(`type`: String = "text", text: String)
  case class ToolResult(content: Seq[ContentBlock], isError: Boolean = false)

  // Parsing
  def parseMessage(line: String): Option[JsonRpcMessage] =
    try
      val json = ujson.read(line)
      val jsonrpc = json("jsonrpc").str
      val method = json.obj.get("method").map(_.str)
      val id = json.obj.get("id")
      val params = json.obj.get("params").getOrElse(ujson.Obj())
      (id, method) match
        case (Some(reqId), Some(m)) => Some(JsonRpcRequest(jsonrpc, reqId, m, params))
        case (None, Some(m)) => Some(JsonRpcNotification(jsonrpc, m, params))
        case _ => None
    catch case _: Exception => None

  // Serialization
  def serializeResponse(resp: JsonRpcResponse): String =
    ujson.Obj("jsonrpc" -> resp.jsonrpc, "id" -> resp.id, "result" -> resp.result).render()

  def serializeError(err: JsonRpcError): String =
    ujson.Obj("jsonrpc" -> err.jsonrpc, "id" -> err.id,
      "error" -> ujson.Obj("code" -> err.error.code, "message" -> err.error.message)).render()

  def initializeResultJson(result: InitializeResult = InitializeResult()): ujson.Value =
    ujson.Obj("protocolVersion" -> result.protocolVersion,
      "capabilities" -> ujson.Obj("tools" -> result.capabilities.tools),
      "serverInfo" -> ujson.Obj("name" -> result.serverInfo.name, "version" -> result.serverInfo.version))

  def toolDefinitionJson(td: ToolDefinition): ujson.Value =
    ujson.Obj("name" -> td.name, "description" -> td.description, "inputSchema" -> td.inputSchema)

  def toolResultJson(result: ToolResult): ujson.Value =
    ujson.Obj("content" -> result.content.map(cb => ujson.Obj("type" -> cb.`type`, "text" -> cb.text)),
      "isError" -> result.isError)
