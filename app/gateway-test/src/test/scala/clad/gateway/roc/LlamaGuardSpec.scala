package clad.gateway.roc

import clad.gateway.upstream.{HttpUpstream, Rt}
import com.sun.net.httpserver.{HttpExchange, HttpServer}
import org.scalatest.funsuite.AnyFunSuite
import java.net.InetSocketAddress
import scala.collection.mutable.ArrayBuffer

class LlamaGuardSpec extends AnyFunSuite:
  final case class Seen(iid: Option[String], body: ujson.Value)

  val profile = ClassifierProfile("llama_guard", "meta-llama/Llama-Guard-4-12B", "v1", "deepinfra", 4, None, None, None)
  val convo = Vector(ujson.Obj("role" -> "user", "content" -> "q"), ujson.Obj("role" -> "assistant", "content" -> "a"))

  def tok(t: String, lp: Option[Double]): ujson.Obj =
    ujson.Obj("token" -> t, "logprob" -> lp.map(ujson.Num(_)).getOrElse(ujson.Null))

  def reply(tokens: Seq[ujson.Obj], withLogprobs: Boolean = true): String =
    val choice = ujson.Obj("message" -> ujson.Obj("content" -> tokens.map(_("token").str).mkString))
    if withLogprobs then choice("logprobs") = ujson.Obj("content" -> ujson.Arr.from(tokens))
    ujson.write(ujson.Obj("choices" -> ujson.Arr(choice)))

  def run[A](responseBody: String)(f: (LlamaGuard, ArrayBuffer[Seen]) => A): A =
    val seen = ArrayBuffer.empty[Seen]
    val server = HttpServer.create(InetSocketAddress(0), 0)
    server.createContext("/", (ex: HttpExchange) =>
      val body = ujson.read(String(ex.getRequestBody.readAllBytes(), "UTF-8"))
      seen.synchronized(seen += Seen(Option(ex.getRequestHeaders.getFirst("X-Clad-Interaction-Id")), body))
      val bytes = responseBody.getBytes("UTF-8")
      ex.sendResponseHeaders(200, bytes.length.toLong)
      ex.getResponseBody.write(bytes)
      ex.close())
    server.start()
    try f(LlamaGuard(HttpUpstream(s"http://localhost:${server.getAddress.getPort}/v1", "k", Rt.base, _ => ()), profile), seen)
    finally server.stop(0)

  test("a safe verdict gives P(unsafe) = 1 - e^lp and every category that bound") {
    val lp = -3.7e-6
    run(reply(Seq(tok("\n\n", Some(0.0)), tok("safe", Some(lp))))) { (g, _) =>
      val s = g.score(convo, "iid").toOption.get
      assert(s.verdict == "safe")
      assert(math.abs(s.unsafe - (1 - math.exp(lp))) < 1e-12 && s.unsafe > 0.0)
      assert(s.categories.nonEmpty && s.categories.values.forall(_ == s.unsafe))
    }
  }

  test("an unsafe verdict with S6 at logprob zero scores 1.0 overall and for S6") {
    run(reply(Seq(tok("\n\n", Some(0.0)), tok("unsafe", Some(0.0)), tok("\n", Some(0.0)), tok("S6", Some(0.0))))) { (g, _) =>
      val s = g.score(convo, "iid").toOption.get
      assert(s.verdict == "unsafe" && s.unsafe == 1.0 && s.categories == Map("S6" -> 1.0))
    }
  }

  test("an unsafe category score is P(unsafe) times the category token probability") {
    val (v, c) = (-0.1, -0.7)
    run(reply(Seq(tok("unsafe", Some(v)), tok("\n", Some(0.0)), tok("S6", Some(c))))) { (g, _) =>
      val s = g.score(convo, "iid").toOption.get
      assert(math.abs(s.unsafe - math.exp(v)) < 1e-12)
      assert(math.abs(s.categories("S6") - math.exp(v) * math.exp(c)) < 1e-12)
    }
  }

  test("a reply with no logprobs is Left") {
    run(reply(Seq(tok("safe", Some(0.0))), withLogprobs = false)) { (g, _) =>
      assert(g.score(convo, "iid") == Left("classifier reply has no verdict logprob"))
    }
  }

  test("a verdict token with a null logprob is Left") {
    run(reply(Seq(tok("safe", None)))) { (g, _) =>
      assert(g.score(convo, "iid") == Left("classifier reply has no verdict logprob"))
    }
  }

  test("an unsafe verdict whose category token has no logprob is Left") {
    run(reply(Seq(tok("unsafe", Some(0.0)), tok("\n", Some(0.0)), tok("S6", None)))) { (g, _) =>
      assert(g.score(convo, "iid").isLeft)
    }
  }

  test("a reply with no verdict token is Left") {
    run(reply(Seq(tok("hello", Some(0.0))))) { (g, _) =>
      assert(g.score(convo, "iid") == Left("classifier reply has no verdict logprob"))
    }
  }

  test("a malformed reply is Left, not an exception") {
    run("""{"choices":[]}""") { (g, _) => assert(g.score(convo, "iid").isLeft) }
  }

  test("an upstream failure is Left") {
    val g = LlamaGuard(HttpUpstream("http://localhost:1/v1", "k", Rt.base, _ => ()), profile)
    assert(g.score(convo, "iid").isLeft)
  }

  test("the request carries the interaction id, logprobs, the model, the conversation and the profile's verdict tokens") {
    run(reply(Seq(tok("safe", Some(0.0))))) { (g, seen) =>
      g.score(convo, "iid-7")
      assert(seen.map(_.iid) == Seq(Some("iid-7")))
      val b = seen.head.body
      assert(b("logprobs").bool && b("model").str == profile.model && b("max_tokens").num.toInt == profile.verdictTokens)
      assert(b("temperature").num == 0.0 && b("messages").arr.toVector == convo)
    }
  }
