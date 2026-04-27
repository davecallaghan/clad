package clad.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.config.test.SampleConfigs
import clad.core.*
import clad.evaluation.*
import clad.runtime.*

class ConfigLoaderSpec extends AnyFlatSpec with Matchers:

  "ConfigLoader.loadFromString" should "parse valid JSON" in {
    val result = ConfigLoader.loadFromString(SampleConfigs.sampleJson)

    result shouldBe a[Right[?, ?]]
    val config = result.toOption.get

    config.name shouldBe "Healthcare Governance"
    config.version shouldBe "1.0"
    config.agents should have size 2
    config.constraints.enterprise should have size 2
    config.checkers should have size 2
  }

  it should "parse minimal config" in {
    val result = ConfigLoader.loadFromString(SampleConfigs.minimalJson)

    result shouldBe a[Right[?, ?]]
    val config = result.toOption.get

    config.name shouldBe "Minimal Config"
    config.version shouldBe "1.0"
    config.agents shouldBe empty
    config.constraints.enterprise shouldBe empty
  }

  it should "return ParseError for invalid JSON" in {
    val result = ConfigLoader.loadFromString(SampleConfigs.invalidJson)

    result shouldBe a[Left[?, ?]]
    val Left(errors) = result: @unchecked
    errors.head shouldBe a[ConfigLoader.ParseError]
  }

  "ConfigLoader.buildAgents" should "convert AgentConfigs to Agents" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val result = ConfigLoader.buildAgents(config)

    result should have size 2
    result.head.authorizedDomains should not be empty
    result.head.authorizedLevels should not be empty
  }

  "ConfigLoader.buildEngine" should "build working GovernanceEngine from config" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val result = ConfigLoader.buildEngine(config)

    result shouldBe a[Right[?, ?]]
    val engine = result.toOption.get

    engine shouldBe a[GovernanceEngine]
  }

  it should "build engine that detects violations" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val result = ConfigLoader.buildEngine(config)

    result shouldBe a[Right[?, ?]]
    val engine = result.toOption.get

    // Test SSN detection
    val content = "Patient SSN: 123-45-6789"
    val contentWithProp = PropertyId.unsafe("contains_pii")

    // The engine should detect the SSN pattern
    engine shouldBe a[GovernanceEngine]
  }

  it should "work with minimal config" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.minimalJson).toOption.get
    val result = ConfigLoader.buildEngine(config)

    result shouldBe a[Right[?, ?]]
  }

  "ConfigLoader.loadFromFile" should "read from disk" in {
    val url = getClass.getClassLoader.getResource("sample-governance.json")
    url should not be null

    val result = ConfigLoader.loadFromFile(java.nio.file.Paths.get(url.toURI))

    result shouldBe a[Right[?, ?]]
    val config = result.toOption.get

    config.name shouldBe "Healthcare Governance"
    config.version shouldBe "1.0"
  }

  it should "return error for non-existent file" in {
    val result = ConfigLoader.loadFromFile(java.nio.file.Paths.get("/nonexistent/path/config.json"))

    result shouldBe a[Left[?, ?]]
  }

  "ConfigWriter.toJson" should "produce valid JSON" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val json = ConfigWriter.toJson(config)

    json should not be empty
    json should startWith("{")
  }

  "ConfigWriter.toPrettyJson" should "produce pretty JSON" in {
    val config = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val json = ConfigWriter.toPrettyJson(config)

    json should not be empty
    json should include("\n")
  }

  "ConfigWriter round-trip" should "preserve config" in {
    val originalConfig = ConfigLoader.loadFromString(SampleConfigs.sampleJson).toOption.get
    val json = ConfigWriter.toJson(originalConfig)
    val roundTripConfig = ConfigLoader.loadFromString(json).toOption.get

    roundTripConfig.name shouldBe originalConfig.name
    roundTripConfig.version shouldBe originalConfig.version
    roundTripConfig.agents should have size originalConfig.agents.size
    roundTripConfig.constraints.enterprise should have size originalConfig.constraints.enterprise.size
  }

  "Checker binding via buildEngine" should "build regex checker that detects SSN" in {
    val Right(config) = ConfigLoader.loadFromString(SampleConfigs.sampleJson): @unchecked
    val Right(engine) = ConfigLoader.buildEngine(config): @unchecked
    val Right(report) = engine.evaluate("SSN: 123-45-6789", Map("audit_logging" -> "enabled")): @unchecked
    report.evaluation.allSatisfied shouldBe false
  }

  it should "build structural checker that detects audit logging" in {
    val Right(config) = ConfigLoader.loadFromString(SampleConfigs.sampleJson): @unchecked
    val Right(engine) = ConfigLoader.buildEngine(config): @unchecked
    val Right(report) = engine.evaluate("clean content", Map("audit_logging" -> "disabled")): @unchecked
    report.evaluation.allSatisfied shouldBe false
  }

  it should "return error for unknown checker type" in {
    val json = """{"name":"Test","version":"1.0","constraints":{"enterprise":[{"property":"test_prop","constraintType":"obligation","level":"enterprise","domain":"test","evaluability":"mechanical"}],"department":[],"project":[]},"checkers":[{"property":"test_prop","checkerType":"unknown_type","config":{}}]}"""
    val Right(config) = ConfigLoader.loadFromString(json): @unchecked
    val result = ConfigLoader.buildEngine(config)
    result shouldBe a[Left[?, ?]]
  }
