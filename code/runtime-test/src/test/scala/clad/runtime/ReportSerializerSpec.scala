package clad.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.test.SampleHierarchies

class ReportSerializerSpec extends AnyFlatSpec with Matchers:

  def buildReport(): GovernanceReport =
    val config = EngineConfig(
      hierarchy = SampleHierarchies.evaluableHierarchy,
      evidence = EvidenceSet.empty,
      level = Level.Project
    )
    val Right(engine) = GovernanceEngine.build(config): @unchecked
    val Right(report) = engine.evaluate(
      "Test prompt for serialization",
      Map("audit_logging" -> "enabled", "transport" -> "tls", "baa_status" -> "current")
    ): @unchecked
    report

  "ReportSerializer.toJson" should "produce non-empty JSON starting with {" in {
    val report = buildReport()
    val json = ReportSerializer.toJson(report)

    json should not be empty
    json.trim should startWith("{")
  }

  "ReportSerializer.fromJson" should "round-trip via toJson" in {
    val report = buildReport()
    val json = ReportSerializer.toJson(report)
    val Right(deserialized) = ReportSerializer.fromJson(json): @unchecked

    deserialized.engineVersion shouldBe report.engineVersion
    deserialized.configDigest shouldBe report.configDigest
    deserialized.evaluation.level shouldBe report.evaluation.level
    deserialized.evaluation.totalCount shouldBe report.evaluation.totalCount
    deserialized.evaluation.allSatisfied shouldBe report.evaluation.allSatisfied
    deserialized.audit.artifactDigest shouldBe report.audit.artifactDigest
    deserialized.audit.entryCount shouldBe report.audit.entryCount
    deserialized.artifact.content shouldBe report.artifact.content
    deserialized.artifact.metadata shouldBe report.artifact.metadata
  }

  "ReportSerializer.toPrettyJson" should "produce indented JSON" in {
    val report = buildReport()
    val prettyJson = ReportSerializer.toPrettyJson(report)

    prettyJson should include("\n")
    prettyJson should include("  ")
  }

  it should "also round-trip via fromJson" in {
    val report = buildReport()
    val prettyJson = ReportSerializer.toPrettyJson(report)
    val Right(deserialized) = ReportSerializer.fromJson(prettyJson): @unchecked

    deserialized.engineVersion shouldBe report.engineVersion
    deserialized.configDigest shouldBe report.configDigest
    deserialized.evaluation.level shouldBe report.evaluation.level
    deserialized.evaluation.totalCount shouldBe report.evaluation.totalCount
    deserialized.evaluation.allSatisfied shouldBe report.evaluation.allSatisfied
    deserialized.audit.artifactDigest shouldBe report.audit.artifactDigest
    deserialized.audit.entryCount shouldBe report.audit.entryCount
    deserialized.artifact.content shouldBe report.artifact.content
    deserialized.artifact.metadata shouldBe report.artifact.metadata
  }

  "JSON output" should "contain expected field names" in {
    val report = buildReport()
    val json = ReportSerializer.toJson(report)

    json should include("engineVersion")
    json should include("configDigest")
    json should include("level")
    json should include("Project")
    json should include("artifactDigest")
    json should include("sha256:")
    json should include("Obligation")
    json should include("phi_access_logging")
  }

  "ReportSerializer.fromJson" should "return Left(ParseError) for empty string" in {
    val result = ReportSerializer.fromJson("")

    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[ReportSerializer.ParseError]
  }

  it should "return Left(ParseError) for invalid JSON" in {
    val result = ReportSerializer.fromJson("not json")

    result shouldBe a[Left[_, _]]
    result.left.get shouldBe a[ReportSerializer.ParseError]
  }
