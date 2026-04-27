package clad.api

import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.config.*
import clad.config.test.SampleConfigs
import clad.api.JsonSupport.given
import java.nio.file.Paths
import java.time.Instant

class GovernanceRoutesSpec extends AnyFlatSpec with Matchers:

  def mkRoutes(): GovernanceRoutes =
    val Right(config) = ConfigLoader.loadFromString(SampleConfigs.sampleJson): @unchecked
    val Right(engine) = ConfigLoader.buildEngine(config): @unchecked
    val engineRef = Ref.unsafe[IO, GovernanceEngine](engine)
    val configRef = Ref.unsafe[IO, GovernanceConfig](config)
    GovernanceRoutes(engineRef, configRef, Paths.get("test.json"), Instant.now())

  def run(routes: GovernanceRoutes, req: Request[IO]): Response[IO] =
    routes.routes.orNotFound.run(req).unsafeRunSync()

  "POST /api/v1/evaluate" should "return 200 with allSatisfied true for clean prompt with audit_logging enabled" in {
    val routes = mkRoutes()
    val json = """{"prompt":"Hello world","metadata":{"logging":"enabled"}}"""
    val req = Request[IO](Method.POST, uri"/api/v1/evaluate")
      .withEntity(json)(EntityEncoder.stringEncoder)

    val resp = run(routes, req)
    resp.status shouldBe Status.Ok

    val body = resp.as[EvaluateResponse].unsafeRunSync()
    body.allSatisfied shouldBe true
    body.totalConstraints should be > 0
    body.unsatisfied shouldBe empty
  }

  it should "return 200 with allSatisfied false for prompt with PII (SSN)" in {
    val routes = mkRoutes()
    val json = """{"prompt":"Patient SSN: 123-45-6789","metadata":{"audit_logging":"enabled"}}"""
    val req = Request[IO](Method.POST, uri"/api/v1/evaluate")
      .withEntity(json)(EntityEncoder.stringEncoder)

    val resp = run(routes, req)
    resp.status shouldBe Status.Ok

    val body = resp.as[EvaluateResponse].unsafeRunSync()
    body.allSatisfied shouldBe false
    body.unsatisfied should not be empty
  }

  "GET /api/v1/config" should "return 200 with config summary" in {
    val routes = mkRoutes()
    val req = Request[IO](Method.GET, uri"/api/v1/config")

    val resp = run(routes, req)
    resp.status shouldBe Status.Ok

    val body = resp.as[ConfigSummary].unsafeRunSync()
    body.name should not be empty
    body.version should not be empty
    body.constraintCount should be > 0
  }

  "GET /api/v1/constraints" should "return 200 with constraint list" in {
    val routes = mkRoutes()
    val req = Request[IO](Method.GET, uri"/api/v1/constraints")

    val resp = run(routes, req)
    resp.status shouldBe Status.Ok

    val body = resp.as[ConstraintListResponse].unsafeRunSync()
    body.total should be > 0
    body.constraints should not be empty
    body.constraints.size shouldBe body.total
  }

  "GET /api/v1/health" should "return 200 with healthy status" in {
    val routes = mkRoutes()
    val req = Request[IO](Method.GET, uri"/api/v1/health")

    val resp = run(routes, req)
    resp.status shouldBe Status.Ok

    val body = resp.as[HealthResponse].unsafeRunSync()
    body.status shouldBe "healthy"
    body.configLoaded shouldBe true
    body.configName should not be empty
    body.uptime should startWith("PT")
  }

  "GET /api/v1/nonexistent" should "return 404" in {
    val routes = mkRoutes()
    val req = Request[IO](Method.GET, uri"/api/v1/nonexistent")

    val resp = run(routes, req)
    resp.status shouldBe Status.NotFound
  }

  "POST /api/v1/evaluate/output" should "return 200 with evaluation response" in {
    val routes = mkRoutes()
    val json = """{"content":"Test output content","metadata":{}}"""
    val req = Request[IO](Method.POST, uri"/api/v1/evaluate/output")
      .withEntity(json)(EntityEncoder.stringEncoder)

    val resp = run(routes, req)
    resp.status shouldBe Status.Ok

    val body = resp.as[OutputEvaluateResponse].unsafeRunSync()
    body.decision should not be empty
    body.totalConstraints shouldBe 0 // empty constraint set
    body.satisfiedCount shouldBe 0
  }
