package clad.gateway.upstream

import clad.gateway.config.Runtime
import clad.gateway.pipeline.PassthroughUpstream

/** Named passthrough upstreams (name to base URL and key). The body goes through unchanged; an empty path posts to the base URL. */
final class HttpPassthrough(upstreams: Map[String, (String, String)], runtime: Runtime, sleep: Long => Unit = Thread.sleep(_))
    extends PassthroughUpstream:
  private val call = HttpCall(runtime, sleep)

  def forward(name: String, path: String, body: String, interactionId: String): Either[String, (Int, String)] =
    upstreams.get(name) match
      case None => Left(s"no passthrough upstream named $name")
      case Some((base, key)) =>
        val root = base.replaceAll("/+$", "")
        val rest = path.replaceAll("^/+", "")
        val url = if rest.isEmpty then root else s"$root/$rest"
        call.post(url, key, interactionId, body).map((status, text) => (status, HttpUpstream.redact(text, key)))
