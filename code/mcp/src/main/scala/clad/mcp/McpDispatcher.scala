package clad.mcp

import clad.runtime.GovernanceEngine
import clad.config.GovernanceConfig

class McpDispatcher(engine: GovernanceEngine, config: GovernanceConfig):
  def dispatch(request: JsonRpcRequest): JsonRpcMessage =
    request.method match
      case "initialize" =>
        JsonRpcResponse("2.0", request.id, McpProtocol.initializeResultJson())
      case "tools/list" =>
        val tools = GovernanceTools.allDefinitions.map(McpProtocol.toolDefinitionJson)
        JsonRpcResponse("2.0", request.id, ujson.Obj("tools" -> tools))
      case "tools/call" =>
        val toolName = request.params("name").str
        val arguments = request.params.obj.get("arguments").getOrElse(ujson.Obj())
        val result = GovernanceTools.execute(toolName, arguments, engine, config)
        JsonRpcResponse("2.0", request.id, McpProtocol.toolResultJson(result))
      case other =>
        JsonRpcError("2.0", request.id, ErrorObject(-32601, s"Method not found: $other"))
