package clad.mdr

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

/** POSTs one alert as JSON. Any status outside 2xx, a timeout or a connection failure is a Left. */
final class Webhook(url: String, timeout: Duration):
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).build()
  private val SuccessClass = 2

  def target: String = url

  def post(alert: ujson.Obj): Either[String, Unit] =
    try
      val req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(ujson.write(alert))).build()
      val status = client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode()
      Either.cond(status / 100 == SuccessClass, (), s"webhook returned HTTP $status")
    catch
      case e: java.io.IOException => Left(s"webhook: ${e.getClass.getSimpleName}: ${e.getMessage}")
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        Left("webhook: interrupted")
      case e: IllegalArgumentException => Left(s"webhook: bad URL: ${e.getMessage}")
