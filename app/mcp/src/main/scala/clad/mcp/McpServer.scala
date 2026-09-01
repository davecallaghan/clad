package clad.mcp

import clad.config.*
import clad.runtime.*
import java.nio.file.Paths

object McpServer:
  def main(args: Array[String]): Unit =
    val configPath = parseConfigPath(args)
    val transport = JsonRpcTransport()
    transport.log(s"Clad MCP Server starting with config: $configPath")

    val config = ConfigLoader.loadFromFile(Paths.get(configPath)) match
      case Right(c) => c
      case Left(errors) =>
        transport.log(s"Failed to load config: ${errors.mkString(", ")}")
        sys.exit(1)
        throw RuntimeException("unreachable")

    val engine = ConfigLoader.buildEngine(config) match
      case Right(e) => e
      case Left(errors) =>
        transport.log(s"Failed to build engine: ${errors.mkString(", ")}")
        sys.exit(1)
        throw RuntimeException("unreachable")

    transport.log(s"Config loaded: ${config.name} v${config.version}")
    val dispatcher = McpDispatcher(engine, config)
    loop(transport, dispatcher)

  @annotation.tailrec
  private def loop(transport: JsonRpcTransport, dispatcher: McpDispatcher): Unit =
    transport.readMessage() match
      case Some(req: JsonRpcRequest) =>
        dispatcher.dispatch(req) match
          case resp: JsonRpcResponse => transport.writeResponse(resp)
          case err: JsonRpcError => transport.writeError(err)
          case _ => ()
        loop(transport, dispatcher)
      case Some(_: JsonRpcNotification) =>
        loop(transport, dispatcher)
      case None =>
        transport.log("stdin closed, shutting down")
      case _ =>
        loop(transport, dispatcher)

  private def parseConfigPath(args: Array[String]): String =
    val idx = args.indexOf("--config")
    if idx >= 0 && idx + 1 < args.length then args(idx + 1)
    else sys.env.getOrElse("CLAD_CONFIG_PATH", "./governance.json")
