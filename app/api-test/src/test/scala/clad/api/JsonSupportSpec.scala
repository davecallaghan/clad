package clad.api

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import clad.api.JsonSupport.given

class JsonSupportSpec extends AnyFlatSpec with Matchers:

  "EntityEncoder" should "encode EvaluateResponse to JSON" in {
    val response = EvaluateResponse(
      allSatisfied = true,
      totalConstraints = 5,
      satisfiedCount = 5,
      unsatisfied = Seq.empty,
      auditDigest = "abc123"
    )

    val entity = summon[EntityEncoder[IO, EvaluateResponse]].toEntity(response)
    val body = entity.body.through(fs2.text.utf8.decode).compile.string.unsafeRunSync()

    body should include("allSatisfied")
    body should include("totalConstraints")
    body should include("abc123")
  }

  "EntityDecoder" should "decode JSON string to EvaluateRequest" in {
    val jsonString = """{"prompt":"test prompt","metadata":{"key1":"value1","key2":"value2"}}"""

    val request = Request[IO](method = Method.POST).withEntity(jsonString)(EntityEncoder.stringEncoder)
    val decoded = request.as[EvaluateRequest].unsafeRunSync()

    decoded.prompt shouldBe "test prompt"
    decoded.metadata shouldBe Map("key1" -> "value1", "key2" -> "value2")
  }

  it should "handle request with default metadata" in {
    val jsonString = """{"prompt":"simple prompt"}"""

    val request = Request[IO](method = Method.POST).withEntity(jsonString)(EntityEncoder.stringEncoder)
    val decoded = request.as[EvaluateRequest].unsafeRunSync()

    decoded.prompt shouldBe "simple prompt"
    decoded.metadata shouldBe Map.empty
  }

  "Round-trip encoding" should "preserve EvaluateResponse fields" in {
    val original = EvaluateResponse(
      allSatisfied = false,
      totalConstraints = 10,
      satisfiedCount = 8,
      unsatisfied = Seq(
        ConstraintSummary("property1", "type1", "WARNING"),
        ConstraintSummary("property2", "type2", "ERROR")
      ),
      auditDigest = "digest456"
    )

    val entity = summon[EntityEncoder[IO, EvaluateResponse]].toEntity(original)
    val jsonString = entity.body.through(fs2.text.utf8.decode).compile.string.unsafeRunSync()

    val request = Request[IO](method = Method.POST).withEntity(jsonString)(EntityEncoder.stringEncoder)
    val decoded = request.as[EvaluateResponse].unsafeRunSync()

    decoded.allSatisfied shouldBe original.allSatisfied
    decoded.totalConstraints shouldBe original.totalConstraints
    decoded.satisfiedCount shouldBe original.satisfiedCount
    decoded.unsatisfied.size shouldBe 2
    decoded.auditDigest shouldBe original.auditDigest
  }

  "ErrorResponse" should "encode correctly" in {
    val error = ErrorResponse(error = "Something went wrong", code = "ERR_500")

    val entity = summon[EntityEncoder[IO, ErrorResponse]].toEntity(error)
    val body = entity.body.through(fs2.text.utf8.decode).compile.string.unsafeRunSync()

    body should include("ERR_500")
    body should include("Something went wrong")
  }

  "HealthResponse" should "encode correctly" in {
    val health = HealthResponse(
      status = "healthy",
      configLoaded = true,
      configName = "test-config",
      configVersion = "1.0.0",
      uptime = "5m 30s"
    )

    val entity = summon[EntityEncoder[IO, HealthResponse]].toEntity(health)
    val body = entity.body.through(fs2.text.utf8.decode).compile.string.unsafeRunSync()

    body should include("healthy")
    body should include("test-config")
    body should include("1.0.0")
  }
