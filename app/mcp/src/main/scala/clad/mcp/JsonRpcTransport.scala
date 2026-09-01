package clad.mcp

import java.io.{BufferedReader, InputStream, InputStreamReader, PrintStream}

class JsonRpcTransport(
  input: InputStream = System.in,
  logStream: PrintStream = System.err,
  responseStream: PrintStream = System.out
):
  private val reader = BufferedReader(InputStreamReader(input))

  def readMessage(): Option[JsonRpcMessage] =
    val line = reader.readLine()
    if line == null then None
    else McpProtocol.parseMessage(line)

  def writeResponse(response: JsonRpcResponse): Unit =
    responseStream.println(McpProtocol.serializeResponse(response))
    responseStream.flush()

  def writeError(error: JsonRpcError): Unit =
    responseStream.println(McpProtocol.serializeError(error))
    responseStream.flush()

  def log(message: String): Unit =
    logStream.println(message)
    logStream.flush()
