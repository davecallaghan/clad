package clad.gateway.http

import clad.audit.Ed25519KeyManagement
import org.scalatest.funsuite.AnyFunSuite
import java.net.{ServerSocket, URI}
import java.time.Duration

class HealthSpec extends AnyFunSuite:
  private val ok = () => Right("fine")

  test("every check passing is healthy and each check reports what it verified") {
    val (healthy, body) = Health(Vector("a" -> ok, "b" -> (() => Right("also fine")))).run
    assert(healthy && body("status").str == "ok")
    assert(body("checks")("b")("detail").str == "also fine" && body("checks")("a")("ok").bool)
  }

  test("one failing check is unhealthy and names the failure; a throwing check is a failure, not a crash") {
    val boom = () => throw IllegalStateException("boom")
    val (healthy, body) = Health(Vector("a" -> ok, "b" -> (() => Left("store down")), "c" -> boom)).run
    assert(!healthy && body("status").str == "unavailable")
    assert(!body("checks")("b")("ok").bool && body("checks")("b")("detail").str == "store down")
    assert(body("checks")("c")("detail").str.contains("boom") && body("checks")("a")("ok").bool)
  }

  test("tcp: an open port passes, a closed one fails") {
    val server = ServerSocket(0)
    try
      assert(Health.tcp(URI.create(s"http://127.0.0.1:${server.getLocalPort}/v1"), Duration.ofSeconds(2))().isRight)
    finally server.close()
    val closed = ServerSocket(0)
    val port = closed.getLocalPort
    closed.close()
    assert(Health.tcp(URI.create(s"http://127.0.0.1:$port/v1"), Duration.ofSeconds(2))().isLeft)
  }

  test("keys: a key pair that signs and verifies passes; a public-only key fails") {
    val (kms, pub) = Ed25519KeyManagement.generate("c0mp0nents000001")
    assert(Health.signs(Vector("components" -> kms))().isRight)
    val publicOnly = Ed25519KeyManagement.fromPublicPem(pub, "c0mp0nents000001").fold(e => fail(e.toString), identity)
    assert(Health.signs(Vector("components" -> publicOnly))().left.exists(_.contains("components")))
  }
