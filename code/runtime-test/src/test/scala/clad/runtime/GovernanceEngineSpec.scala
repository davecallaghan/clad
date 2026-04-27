package clad.runtime

import clad.core.*
import clad.evaluation.*
import clad.runtime.test.SampleHierarchies
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.EitherValues

class GovernanceEngineSpec extends AnyWordSpec with Matchers with EitherValues:
  import SampleHierarchies.*

  "GovernanceEngine.build" should {
    "succeed with all-mechanical config" in {
      val config = EngineConfig(
        hierarchy = evaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val result = GovernanceEngine.build(config)
      result.isRight shouldBe true
    }

    "succeed with mixed config when evidence provided" in {
      val config = EngineConfig(
        hierarchy = mixedEvaluableHierarchy,
        evidence = sampleEvidence(satisfied = true),
        level = Level.Enterprise
      )
      val result = GovernanceEngine.build(config)
      result.isRight shouldBe true
    }

    "fail when evidence missing for procedural constraints" in {
      val config = EngineConfig(
        hierarchy = mixedEvaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val result = GovernanceEngine.build(config)
      result.isLeft shouldBe true
      result.left.value should have size 1
      result.left.value.head match {
        case GovernanceEngine.EvidenceGap(missing) =>
          missing should contain(reviewProp)
        case _ => fail("Expected EvidenceGap")
      }
    }
  }

  "GovernanceEngine.evaluate" should {
    "produce complete report for compliant prompt" in {
      val config = EngineConfig(
        hierarchy = evaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val engine = GovernanceEngine.build(config).value

      val compliantPrompt = "Request patient data summary with no PII"

      val result = engine.evaluate(
        compliantPrompt,
        Map("audit_logging" -> "enabled", "transport" -> "tls", "baa_status" -> "current")
      )
      result.isRight shouldBe true

      val report = result.value
      report.engineVersion shouldBe "0.1.0-SNAPSHOT"
      report.artifact.content shouldBe compliantPrompt
      report.evaluation.results should have size 2 // Enterprise level only
      report.audit.entries should have size 2

      // All constraints should be satisfied
      report.evaluation.results.foreach { cr =>
        cr.satisfied shouldBe true
      }
    }

    "detect violations in non-compliant prompt" in {
      val config = EngineConfig(
        hierarchy = evaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val engine = GovernanceEngine.build(config).value

      val nonCompliantPrompt = """
        |Request patient data for ID 12345
        |SSN: 123-45-6789
        |audit_logging=disabled
        |transport=http
      """.stripMargin.trim

      val result = engine.evaluate(nonCompliantPrompt)
      result.isRight shouldBe true

      val report = result.value
      val violations = report.evaluation.results.filter(!_.satisfied)
      violations should not be empty

      // Should detect PII (SSN)
      violations.exists(_.constraint.property == piiProp) shouldBe true
    }

    "produce audit record linked to artifact digest" in {
      val config = EngineConfig(
        hierarchy = evaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val engine = GovernanceEngine.build(config).value

      val prompt = "Test prompt"
      val result = engine.evaluate(prompt)
      result.isRight shouldBe true

      val report = result.value
      report.audit.artifactDigest should startWith("sha256:")
      report.audit.configDigest should startWith("sha256:")
      report.audit.artifactDigest should not be empty
    }

    "evaluate at configured level only" in {
      val enterpriseConfig = EngineConfig(
        hierarchy = evaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val enterpriseEngine = GovernanceEngine.build(enterpriseConfig).value

      val prompt = """
        |Request data
        |audit_logging=enabled
        |transport=tls
        |baa_status=current
      """.stripMargin.trim

      val enterpriseResult = enterpriseEngine.evaluate(prompt)
      enterpriseResult.isRight shouldBe true

      val enterpriseReport = enterpriseResult.value
      // Enterprise level has 2 constraints (obligation + prohibition)
      enterpriseReport.evaluation.results should have size 2
      enterpriseReport.audit.entries should have size 2
    }

    "handle mixed mechanical/procedural" in {
      val config = EngineConfig(
        hierarchy = mixedEvaluableHierarchy,
        evidence = sampleEvidence(satisfied = true),
        level = Level.Enterprise
      )
      val engine = GovernanceEngine.build(config).value

      val prompt = "audit_logging=enabled"
      val result = engine.evaluate(prompt)
      result.isRight shouldBe true

      val report = result.value
      report.evaluation.results should have size 2 // mechanical + procedural

      val mechanicalResults = report.evaluation.mechanicalResults
      val proceduralResults = report.evaluation.proceduralResults

      mechanicalResults should have size 1
      proceduralResults should have size 1

      // Check audit entries have correct evaluability class
      val auditClasses = report.audit.entries.map(_.evaluabilityClass).toSet
      auditClasses should contain(EvaluabilityClass.Mechanical)
      auditClasses should contain(EvaluabilityClass.Procedural)
    }

    "produce deterministic configDigest for same config" in {
      val config = EngineConfig(
        hierarchy = evaluableHierarchy,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val engine1 = GovernanceEngine.build(config).value
      val engine2 = GovernanceEngine.build(config).value

      val prompt = "test"
      val result1 = engine1.evaluate(prompt)
      val result2 = engine2.evaluate(prompt)

      result1.isRight shouldBe true
      result2.isRight shouldBe true

      result1.value.configDigest shouldBe result2.value.configDigest
    }

    "succeed with empty hierarchy" in {
      val emptyHierarchy = ConstraintHierarchy.build(Set.empty, Set.empty, Set.empty).toOption.get
      val Right(emptyCheckerRegistry) = CheckerRegistry.build(Seq.empty): @unchecked
      val Right(emptyEvaluable) = EvaluableHierarchy.build(emptyHierarchy, Set.empty, emptyCheckerRegistry): @unchecked

      val config = EngineConfig(
        hierarchy = emptyEvaluable,
        evidence = EvidenceSet.empty,
        level = Level.Enterprise
      )
      val engine = GovernanceEngine.build(config).value

      val prompt = "test prompt"
      val result = engine.evaluate(prompt)
      result.isRight shouldBe true

      val report = result.value
      report.evaluation.results shouldBe empty
      report.audit.entries shouldBe empty
    }
  }
