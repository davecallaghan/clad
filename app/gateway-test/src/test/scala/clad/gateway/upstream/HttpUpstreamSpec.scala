package clad.gateway.upstream

import clad.gateway.config.{Fixtures, Runtime}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.scalatest.funsuite.AnyFunSuite
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ArrayBuffer

/** The only place test Runtime values are built: every variant derives from the shared fixture. */
object Rt:
  val base: Runtime = Fixtures.runtime
  val fastTimeout: Runtime = base.copy(upstreamTimeoutSeconds = 1)
  val oneRetry: Runtime = base.copy(upstreamRetries = 1)

class HttpUpstreamSpec extends AnyFunSuite:
  final case class Seen(path: String, auth: Option[String], iid: Option[String], body: String)

  def withServer[A](handler: (HttpExchange, Seen, Int) => Unit)(f: (String, ArrayBuffer[Seen]) => A): A =
    val seen = ArrayBuffer.empty[Seen]
    val count = AtomicInteger(0)
    val server = HttpServer.create(InetSocketAddress(0), 0)
    server.createContext("/", (ex: HttpExchange) =>
      val body = String(ex.getRequestBody.readAllBytes(), "UTF-8")
      val s = Seen(ex.getRequestURI.getPath, Option(ex.getRequestHeaders.getFirst("Authorization")),
        Option(ex.getRequestHeaders.getFirst("X-Clad-Interaction-Id")), body)
      seen.synchronized(seen += s)
      handler(ex, s, count.incrementAndGet())
      ex.close())
    server.start()
    try f(s"http://localhost:${server.getAddress.getPort}/v1", seen)
    finally server.stop(0)

  def reply(ex: HttpExchange, status: Int, body: String): Unit =
    val bytes = body.getBytes("UTF-8")
    ex.sendResponseHeaders(status, bytes.length.toLong)
    ex.getResponseBody.write(bytes)

  val ok = """{"choices":[{"message":{"content":"hi"}}]}"""
  def req = ujson.Obj("model" -> "m")

  test("request carries bearer key and interaction id, reply comes back") {
    withServer((ex, _, _) => reply(ex, 200, ok)) { (url, seen) =>
      val r = HttpUpstream(url, "k", Rt.base, _ => ()).complete(req, "iid")
      assert(r.exists(_("choices").arr.nonEmpty))
      assert(seen.map(_.path) == Seq("/v1/chat/completions"))
      assert(seen.map(_.auth) == Seq(Some("Bearer k")) && seen.map(_.iid) == Seq(Some("iid")))
    }
  }

  test("plain-http requests carry no h2c upgrade headers, for chat and for passthrough") {
    val upgrade = ArrayBuffer.empty[(Option[String], Option[String], Option[String])]
    def note(ex: HttpExchange): Unit =
      val h = ex.getRequestHeaders
      upgrade.synchronized(upgrade += ((Option(h.getFirst("Upgrade")), Option(h.getFirst("HTTP2-Settings")), Option(h.getFirst("Connection")))))
    withServer((ex, _, _) => { note(ex); reply(ex, 200, ok) }) { (url, _) =>
      assert(HttpUpstream(url, "k", Rt.base, _ => ()).complete(req, "iid").isRight)
      assert(HttpPassthrough(Map("jev" -> (url, "k")), Rt.base).forward("jev", "x", "{}", "iid").isRight)
    }
    assert(upgrade.size == 2)
    assert(upgrade.forall((u, settings, conn) => u.isEmpty && settings.isEmpty && !conn.exists(_.toLowerCase.contains("upgrade"))), upgrade.toString)
  }

  test("429 then 200 retries once with the configured wait") {
    val sleeps = ArrayBuffer.empty[Long]
    withServer((ex, _, n) => if n == 1 then reply(ex, 429, "slow") else reply(ex, 200, ok)) { (url, seen) =>
      val r = HttpUpstream(url, "k", Rt.oneRetry, sleeps += _).complete(req, "iid")
      assert(r.isRight && seen.size == 2)
      assert(sleeps.toSeq == Seq(Rt.oneRetry.upstreamRetryWaitMillis.toLong))
    }
  }

  test("503 twice with one retry gives Left naming 503 and never leaks the key") {
    withServer((ex, _, _) => reply(ex, 503, "down")) { (url, seen) =>
      val r = HttpUpstream(url, "secret-key", Rt.oneRetry, _ => ()).complete(req, "iid")
      assert(r.left.exists(m => m.contains("503") && !m.contains("secret-key")))
      assert(seen.size == 2)
    }
  }

  test("a non-retryable error status is Left without retry") {
    withServer((ex, _, _) => reply(ex, 400, "bad")) { (url, seen) =>
      val r = HttpUpstream(url, "k", Rt.oneRetry, _ => ()).complete(req, "iid")
      assert(r.left.exists(_.contains("400")) && seen.size == 1)
    }
  }

  test("a 200 with an HTML body is Left") {
    withServer((ex, _, _) => reply(ex, 200, "<html>")) { (url, _) =>
      assert(HttpUpstream(url, "k", Rt.base, _ => ()).complete(req, "iid").isLeft)
    }
  }

  test("a handler slower than the timeout is Left cannot reach") {
    withServer((ex, _, _) => { Thread.sleep(2000); reply(ex, 200, ok) }) { (url, _) =>
      val r = HttpUpstream(url, "k", Rt.fastTimeout, _ => ()).complete(req, "iid")
      assert(r.left.exists(_.contains("cannot reach")))
    }
  }

  test("a closed port is Left cannot reach") {
    val r = HttpUpstream("http://localhost:1/v1", "k", Rt.base, _ => ()).complete(req, "iid")
    assert(r.left.exists(_.contains("cannot reach")))
  }

  test("passthrough posts the body byte for byte to base/path and returns status and body") {
    withServer((ex, _, _) => reply(ex, 201, "made")) { (url, seen) =>
      val body = """{"a": 1,  "b":"é"}"""
      val r = HttpPassthrough(Map("jev" -> (url, "k")), Rt.base).forward("jev", "chat/x", body, "iid")
      assert(r == Right((201, "made")))
      assert(seen.map(_.path) == Seq("/v1/chat/x") && seen.map(_.body) == Seq(body))
      assert(seen.map(_.auth) == Seq(Some("Bearer k")) && seen.map(_.iid) == Seq(Some("iid")))
    }
  }

  test("passthrough with an empty path posts to the base URL itself") {
    withServer((ex, _, _) => reply(ex, 200, "x")) { (url, seen) =>
      HttpPassthrough(Map("jev" -> (url, "k")), Rt.base).forward("jev", "", "{}", "iid")
      assert(seen.map(_.path) == Seq("/v1"))
    }
  }

  test("passthrough to an unknown name is Left") {
    assert(HttpPassthrough(Map.empty, Rt.base).forward("nope", "", "{}", "iid").isLeft)
  }

  test("an interrupt during the back-off sleep is Left and restores the interrupt flag") {
    withServer((ex, _, _) => reply(ex, 429, "slow")) { (url, seen) =>
      val r = HttpUpstream(url, "k", Rt.oneRetry, _ => throw InterruptedException()).complete(req, "iid")
      val flagged = Thread.interrupted()
      assert(r.left.exists(_.contains("cannot reach the provider: java.lang.InterruptedException")))
      assert(flagged && seen.size == 1)
    }
  }

  test("passthrough joins a trailing-slash base and a leading-slash path with one slash") {
    withServer((ex, _, _) => reply(ex, 200, "x")) { (url, seen) =>
      HttpPassthrough(Map("jev" -> (url + "/", "k")), Rt.base).forward("jev", "/x", "{}", "iid")
      assert(seen.map(_.path) == Seq("/v1/x"))
    }
  }

  test("a provider body that echoes the key is redacted in the error") {
    withServer((ex, _, _) => reply(ex, 401, "bad key secret-key-123 given")) { (url, _) =>
      val r = HttpUpstream(url, "secret-key-123", Rt.base, _ => ()).complete(req, "iid")
      assert(r.left.exists(m => m.contains("401") && m.contains("***") && !m.contains("secret-key-123")))
    }
  }

  test("a passthrough body that echoes the key is redacted, whatever the status") {
    for status <- Seq(200, 401) do
      withServer((ex, _, _) => reply(ex, status, """{"echo":"secret-key-123"}""")) { (url, _) =>
        val r = HttpPassthrough(Map("jev" -> (url, "secret-key-123")), Rt.base).forward("jev", "", "{}", "iid")
        assert(r == Right((status, """{"echo":"***"}""")))
      }
  }
