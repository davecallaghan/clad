package clad.gateway.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import clad.audit.Ed25519KeyManagement
import clad.gateway.config.Fixtures
import clad.gateway.epg.Epg
import clad.gateway.http.{Clients, Routes}
import clad.gateway.pipeline.*
import clad.gateway.store.MemoryStore
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.scalatest.funsuite.AnyFunSuite
import org.typelevel.ci.CIString
import java.nio.file.Files
import java.time.Instant

class RoutesSpec extends AnyFunSuite:
  private val (components, _) = Ed25519KeyManagement.generate("c0mp0nents000001")
  private val (supervisor, _) = Ed25519KeyManagement.generate("5upervis0r000001")
  private val now = Instant.parse("2026-10-16T12:00:00Z")
  private val Admin = "admin-secret"

  private final class Rig(approved: Boolean = true, pass: FakePassthrough = FakePassthrough()):
    val cfg = if approved then Fixtures.approvedConfig() else Fixtures.config
    val up = FakeUpstream()
    val gw = Gateway(cfg, Epg.build(cfg).fold(e => throw AssertionError(e), identity), MemoryStore(), components, supervisor, up,
      pass, Files.createTempDirectory("contain").resolve("contained.json"), () => now)
    val clients = Clients.parse("tok-api=api,tok-planner=planner", Admin).fold(e => throw AssertionError(e), identity)
    val client = Client.fromHttpApp(Routes(gw, clients).routes.orNotFound)

  private def post(rig: Rig, uri: String, token: Option[String], body: String, headers: Header.ToRaw*): (Status, ujson.Value, Option[String]) =
    val req = Request[IO](Method.POST, Uri.unsafeFromString(uri)).withEntity(body).putHeaders(headers*)
    val withAuth = token.fold(req)(t => req.putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, t))))
    rig.client.run(withAuth).use { r =>
      r.as[String].map(t => (r.status, if t.isEmpty then ujson.Null else ujson.read(t), r.headers.get(CIString("X-Clad-Interaction-Id")).map(_.head.value)))
    }.unsafeRunSync()

  private def chat(template: String = "agent.answer", principal: String = "api", messages: String = """[{"role":"user","content":"hi"}]""") =
    s"""{"model":"ignored","messages":$messages,"clad":{"template_id":"$template","principal":"$principal"}}"""

  private def errType(j: ujson.Value) = j("error")("type").str

  test("200 carries the provider response, the clad block and the interaction header") {
    val (status, body, header) = post(Rig(), "/v1/chat/completions", Some("tok-api"), chat())
    assert(status == Status.Ok && body("choices").arr.nonEmpty)
    assert(header.contains(body("clad")("interaction_id").str) && body("clad")("outcome").str == "pass")
  }

  test("403 for an unattested template names the interaction") {
    val (status, body, header) = post(Rig(approved = false), "/v1/chat/completions", Some("tok-api"), chat())
    assert(status == Status.Forbidden && errType(body) == "governance_block")
    assert(header.contains(body("error")("interaction_id").str))
  }

  test("400 for a system message") {
    val (status, body, _) = post(Rig(), "/v1/chat/completions", Some("tok-api"), chat(messages = """[{"role":"system","content":"x"}]"""))
    assert(status == Status.BadRequest && errType(body) == "governance_rejected")
  }

  test("401 without a token and with an unknown token") {
    assert(post(Rig(), "/v1/chat/completions", None, chat())._1 == Status.Unauthorized)
    assert(post(Rig(), "/v1/chat/completions", Some("nope"), chat())._1 == Status.Unauthorized)
  }

  test("400 principal_not_allowed_for_token for planner:u with the api token; the prefix itself and prefix:x are allowed") {
    val rig = Rig()
    val (status, body, _) = post(rig, "/v1/chat/completions", Some("tok-api"), chat(principal = "planner:u"))
    assert(status == Status.BadRequest && body("error")("reason").str == "principal_not_allowed_for_token")
    assert(post(rig, "/v1/chat/completions", Some("tok-api"), chat(principal = "apix"))._1 == Status.BadRequest)
    assert(post(rig, "/v1/chat/completions", Some("tok-api"), chat(principal = "api:u"))._1 == Status.Ok)
    assert(rig.up.calls.size == 1)
  }

  test("malformed bodies are 400 governance_rejected, never 500") {
    val rig = Rig()
    val bodies = Seq("not json", "[]", """{"messages":[]}""", """{"messages":[],"clad":"x"}""",
      """{"messages":[],"clad":{"principal":"api"}}""", """{"messages":[],"clad":{"template_id":1,"principal":"api"}}""",
      """{"clad":{"template_id":"agent.answer","principal":"api"}}""",
      """{"messages":"x","clad":{"template_id":"agent.answer","principal":"api"}}""",
      """{"messages":[1],"clad":{"template_id":"agent.answer","principal":"api"}}""",
      """{"messages":[],"clad":{"template_id":"agent.answer","principal":"api","vars":{"a":1}}}""",
      """{"messages":[],"clad":{"template_id":"agent.answer","principal":"api","vars":[]}}""",
      """{"messages":[],"clad":{"template_id":"agent.answer","principal":"api","session":5}}""",
      """{"messages":[{"role":5}],"clad":{"template_id":"agent.answer","principal":"api"}}""")
    for b <- bodies do
      val (status, body, _) = post(rig, "/v1/chat/completions", Some("tok-api"), b)
      assert(status == Status.BadRequest && errType(body) == "governance_rejected", b)
  }

  private val passHeaders = Seq[Header.ToRaw]("X-Clad-Template" -> "router.jev", "X-Clad-Principal" -> "api:u", "X-Clad-Session" -> "s1")

  test("passthrough returns the upstream's status and body, with the interaction header") {
    val rig = Rig()
    val (status, body, header) = post(rig, "/v1/passthrough/jev/chat/x", Some("tok-api"), """{"a":1}""", passHeaders*)
    assert(status == Status.Ok && header.isDefined && body == ujson.Obj())
  }

  test("passthrough with an empty path reaches the upstream with an empty path") {
    val pass = FakePassthrough()
    val (status, _, header) = post(Rig(pass = pass), "/v1/passthrough/jev", Some("tok-api"), "{}", passHeaders*)
    assert(status == Status.Ok && header.isDefined)
    assert(pass.calls.map((n, p, b, _) => (n, p, b)).toSeq == Seq(("jev", "", "{}")))
  }

  test("passthrough keeps a multi-segment path and the upstream's non-200 status") {
    val pass = new FakePassthrough:
      override def forward(name: String, path: String, body: String, iid: String) = Right((418, "teapot"))
    val (status, _, _) = {
      val rig = Rig(pass = pass)
      val req = Request[IO](Method.POST, uri"/v1/passthrough/jev/a/b").withEntity("{}")
        .putHeaders(passHeaders*).putHeaders(Authorization(Credentials.Token(AuthScheme.Bearer, "tok-api")))
      rig.client.run(req).use(r => r.as[String].map(t => (r.status, t, ()))).unsafeRunSync()
    }
    assert(status.code == 418)
  }

  test("passthrough to an upstream the template does not declare is a 400 and reaches nothing") {
    val pass = FakePassthrough()
    val (status, body, _) = post(Rig(pass = pass), "/v1/passthrough/openai/chat/completions", Some("tok-api"), """{"model":"x","messages":[]}""", passHeaders*)
    assert(status == Status.BadRequest && errType(body) == "governance_rejected" && pass.calls.isEmpty)
  }

  test("passthrough needs a token, the headers and an allowed principal") {
    val rig = Rig()
    assert(post(rig, "/v1/passthrough/jev", None, "{}", passHeaders*)._1 == Status.Unauthorized)
    assert(post(rig, "/v1/passthrough/jev", Some("tok-api"), "{}")._1 == Status.BadRequest)
    val other = Seq[Header.ToRaw]("X-Clad-Template" -> "router.jev", "X-Clad-Principal" -> "planner:u")
    val (status, body, _) = post(rig, "/v1/passthrough/jev", Some("tok-api"), "{}", other*)
    assert(status == Status.BadRequest && body("error")("reason").str == "principal_not_allowed_for_token")
  }

  test("health needs no token") {
    val rig = Rig()
    val (status, body) = rig.client.run(Request[IO](Method.GET, uri"/v1/health")).use(r => r.as[String].map(t => (r.status, ujson.read(t)))).unsafeRunSync()
    assert(status == Status.Ok && body("status").str == "ok")
  }

  test("contain then call is 403 contained; release then call is no longer contained") {
    val rig = Rig()
    val c = post(rig, "/admin/contain", Some(Admin), """{"project":"agent","reason":"incident"}""")
    assert(c._1 == Status.Ok)
    val (status, body, _) = post(rig, "/v1/chat/completions", Some("tok-api"), chat())
    assert(status == Status.Forbidden && body("error")("reason").str == "contained")
    assert(post(rig, "/admin/release", Some(Admin), """{"project":"agent"}""")._1 == Status.Ok)
    assert(post(rig, "/v1/chat/completions", Some("tok-api"), chat())._1 == Status.Ok)
  }

  test("admin routes refuse a client token, no token, and bad bodies or unknown projects are 400") {
    val rig = Rig()
    assert(post(rig, "/admin/contain", Some("tok-api"), """{"project":"agent","reason":"x"}""")._1 == Status.Unauthorized)
    assert(post(rig, "/admin/release", None, """{"project":"agent"}""")._1 == Status.Unauthorized)
    assert(post(rig, "/admin/contain", Some(Admin), "nope")._1 == Status.BadRequest)
    assert(post(rig, "/admin/contain", Some(Admin), """{"project":"nope","reason":"x"}""")._1 == Status.BadRequest)
    assert(post(rig, "/v1/chat/completions", Some(Admin), chat())._1 == Status.Unauthorized)
  }

  test("Clients.parse accepts a spec and rejects malformed ones") {
    assert(Clients.parse("a=api, b=planner", "z") == Right(Clients(Map("a" -> "api", "b" -> "planner"), "z")))
    for bad <- Seq("", "a", "a=", "=api", "a=api,a=planner", "z=api") do assert(Clients.parse(bad, "z").isLeft, bad)
    assert(Clients.parse("a=api", "").isLeft)
  }

  test("passthrough refuses traversal, query, fragment and slash smuggling in path segments") {
    val pass = FakePassthrough()
    val rig = Rig(pass = pass)
    for p <- Seq("/v1/passthrough/jev/%2E%2E/x", "/v1/passthrough/jev/a%3Fb", "/v1/passthrough/jev/a%2Fb",
      "/v1/passthrough/jev/a%23b", "/v1/passthrough/jev/a%20b", "/v1/passthrough/jev/./x", "/v1/passthrough/%2E%2E") do
      val (status, body, _) = post(rig, p, Some("tok-api"), "{}", passHeaders*)
      assert(status == Status.BadRequest && errType(body) == "governance_rejected", p)
    assert(pass.calls.isEmpty)
    assert(post(rig, "/v1/passthrough/jev/v1.2/a_b-c~d", Some("tok-api"), "{}", passHeaders*)._1 == Status.Ok)
    assert(pass.calls.map(_._2).toSeq == Seq("v1.2/a_b-c~d"))
  }

  test("Clients.toString shows prefixes and a count, never a token") {
    val c = Clients.parse("tok-api=api,tok-planner=planner", Admin).fold(e => throw AssertionError(e), identity)
    assert(!c.toString.contains("tok-") && !c.toString.contains(Admin) && c.toString.contains("2 tokens") && c.toString.contains("planner"))
  }

  test("a delivered passthrough reply with an unusable status is 502 with an error body, not a 500") {
    val pass = new FakePassthrough:
      override def forward(name: String, path: String, body: String, iid: String) = Right((1000, "x"))
    val (status, body, header) = post(Rig(pass = pass), "/v1/passthrough/jev", Some("tok-api"), "{}", passHeaders*)
    assert(status == Status.BadGateway && errType(body) == "unavailable" && header.isDefined)
  }

  test("a principal of exactly <prefix>: is refused") {
    val (status, body, _) = post(Rig(), "/v1/chat/completions", Some("tok-api"), chat(principal = "api:"))
    assert(status == Status.BadRequest && body("error")("reason").str == "principal_not_allowed_for_token")
  }
