package clad.gateway.upstream

import clad.gateway.config.Runtime
import clad.gateway.pipeline.Upstream
import scala.util.Try

/** Chat completions client. Left means the provider is unavailable. */
final class HttpUpstream(baseUrl: String, apiKey: String, runtime: Runtime, sleep: Long => Unit = Thread.sleep(_))
    extends Upstream:
  private val call = HttpCall(runtime, sleep)

  def complete(body: ujson.Obj, interactionId: String): Either[String, ujson.Value] =
    call.post(s"$baseUrl/chat/completions", apiKey, interactionId, ujson.write(body)).flatMap { (status, text) =>
      if status / 100 != 2 then Left(s"provider returned $status: ${HttpUpstream.excerpt(text, apiKey)}")
      else Try(ujson.read(text)).toEither.left.map(e => s"provider returned $status: unparseable body (${e.getClass.getName})")
    }

object HttpUpstream:
  /** How much of an error body goes into a message: enough to diagnose, short enough not to flood the audit reason. */
  private val excerptChars = 200
  /** The configured key is redacted before truncating, so a provider that echoes it cannot leak it into an error. */
  private[upstream] def excerpt(text: String, key: String): String =
    (if key.isEmpty then text else text.replace(key, "***")).take(excerptChars)
