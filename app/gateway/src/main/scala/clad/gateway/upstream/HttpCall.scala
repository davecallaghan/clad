package clad.gateway.upstream

import clad.gateway.config.Runtime
import java.io.IOException
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/** One POST with the configured timeout and retries. Never throws: every failure is a Left naming the exception class only,
  * so neither the key nor the request body can reach an error message. */
private[upstream] final class HttpCall(runtime: Runtime, sleep: Long => Unit):
  // HTTP/1.1 only: on plain http the default HTTP/2 client sends an h2c Upgrade, which the egress proxy forwards and the provider rejects
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build()

  def post(url: String, key: String, interactionId: String, body: String): Either[String, (Int, String)] =
    attempt(url, key, interactionId, body, runtime.upstreamRetries)

  private def attempt(url: String, key: String, interactionId: String, body: String, retriesLeft: Int): Either[String, (Int, String)] =
    send(url, key, interactionId, body) match
      case Right((status, _)) if (status == 429 || status == 503) && retriesLeft > 0 =>
        pause().flatMap(_ => attempt(url, key, interactionId, body, retriesLeft - 1))
      case other => other

  private def pause(): Either[String, Unit] =
    try Right(sleep(runtime.upstreamRetryWaitMillis.toLong))
    catch
      case e: InterruptedException =>
        Thread.currentThread.interrupt()
        Left(s"cannot reach the provider: ${e.getClass.getName}")

  private def send(url: String, key: String, interactionId: String, body: String): Either[String, (Int, String)] =
    try
      val request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(runtime.upstreamTimeoutSeconds.toLong))
        .header("Content-Type", "application/json")
        .header("Authorization", s"Bearer $key")
        .header("X-Clad-Interaction-Id", interactionId)
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofString())
      Right((response.statusCode, response.body))
    catch
      case e: IOException => Left(s"cannot reach the provider: ${e.getClass.getName}") // includes HttpTimeoutException
      case e: InterruptedException =>
        Thread.currentThread.interrupt()
        Left(s"cannot reach the provider: ${e.getClass.getName}")
      case e: IllegalArgumentException => Left(s"cannot reach the provider: ${e.getClass.getName}")
