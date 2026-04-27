package clad.evaluation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import clad.core.*
import clad.evaluation.test.TestCheckers.*

class DeonticInversionSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")

  def buildEvaluableHierarchy(
    constraint: Constraint,
    checker: PropertyChecker
  ): EvaluableHierarchy =
    val hierarchy = ConstraintHierarchy.build(
      Set(constraint), Set.empty, Set.empty
    ).toOption.get
    val mc = MechanicalConstraint(constraint, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(checker)): @unchecked
    EvaluableHierarchy.build(hierarchy, Set(mc), registry).toOption.get

  // O(φ): property detected → satisfied
  "Obligation with property detected" should "be satisfied" in {
    val c = Constraint.Obligation(phi, Level.Enterprise)
    val eh = buildEvaluableHierarchy(c, AlwaysDetects(phi))
    val Right(report) = PromptEvaluator.evaluate(
      PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, java.time.Instant.now()
    ): @unchecked

    report.results should have size 1
    val result = report.results.head
    result.satisfied shouldBe true
    result.detail shouldBe a[MechanicalDetail]
    result.detail.asInstanceOf[MechanicalDetail].propertyDetected shouldBe true
  }

  // O(φ): property NOT detected → NOT satisfied
  "Obligation with property not detected" should "not be satisfied" in {
    val c = Constraint.Obligation(phi, Level.Enterprise)
    val eh = buildEvaluableHierarchy(c, NeverDetects(phi))
    val Right(report) = PromptEvaluator.evaluate(
      PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, java.time.Instant.now()
    ): @unchecked

    val result = report.results.head
    result.satisfied shouldBe false
    result.detail.asInstanceOf[MechanicalDetail].propertyDetected shouldBe false
  }

  // F(φ): property detected → NOT satisfied (this is the inversion)
  "Prohibition with property detected" should "not be satisfied" in {
    val c = Constraint.Prohibition(phi, Level.Enterprise)
    val eh = buildEvaluableHierarchy(c, AlwaysDetects(phi))
    val Right(report) = PromptEvaluator.evaluate(
      PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, java.time.Instant.now()
    ): @unchecked

    val result = report.results.head
    result.satisfied shouldBe false
    result.detail.asInstanceOf[MechanicalDetail].propertyDetected shouldBe true
  }

  // F(φ): property NOT detected → satisfied
  "Prohibition with property not detected" should "be satisfied" in {
    val c = Constraint.Prohibition(phi, Level.Enterprise)
    val eh = buildEvaluableHierarchy(c, NeverDetects(phi))
    val Right(report) = PromptEvaluator.evaluate(
      PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, java.time.Instant.now()
    ): @unchecked

    val result = report.results.head
    result.satisfied shouldBe true
    result.detail.asInstanceOf[MechanicalDetail].propertyDetected shouldBe false
  }

  // Keyword-based: O(φ) with keyword present
  "Obligation with keyword checker and keyword present" should "be satisfied" in {
    val c = Constraint.Obligation(phi, Level.Enterprise)
    val eh = buildEvaluableHierarchy(c, KeywordChecker(phi, "HIPAA"))
    val Right(report) = PromptEvaluator.evaluate(
      PromptArtifact("This has a HIPAA disclaimer"), eh, EvidenceSet.empty, Level.Enterprise, java.time.Instant.now()
    ): @unchecked

    report.results.head.satisfied shouldBe true
  }

  // Keyword-based: F(φ) with keyword present
  "Prohibition with keyword checker and keyword present" should "not be satisfied" in {
    val c = Constraint.Prohibition(phi, Level.Enterprise)
    val eh = buildEvaluableHierarchy(c, KeywordChecker(phi, "HIPAA"))
    val Right(report) = PromptEvaluator.evaluate(
      PromptArtifact("This has a HIPAA disclaimer"), eh, EvidenceSet.empty, Level.Enterprise, java.time.Instant.now()
    ): @unchecked

    report.results.head.satisfied shouldBe false
  }
