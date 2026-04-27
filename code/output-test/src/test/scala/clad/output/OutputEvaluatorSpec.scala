package clad.output

import clad.core.*
import clad.output.test.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class OutputEvaluatorSpec extends AnyFlatSpec with Matchers:
  import SampleOutputConstraints.*

  val now: Instant = Instant.parse("2026-04-22T18:00:00Z")

  "OutputEvaluator" should "pass when all constraints satisfied" in {
    val cleanArtifact = OutputArtifact("Clean output with no violations", Map.empty, None)
    val report = OutputEvaluator.evaluate(cleanArtifact, standardConstraintSet, now)

    report.decision shouldBe PipelineDecision.Pass
    report.totalCount shouldBe 2
    report.satisfiedCount shouldBe 2
    report.unsatisfied shouldBe empty
  }

  it should "pass for empty constraint set" in {
    val artifact = OutputArtifact("Any content", Map.empty, None)
    val emptyConstraints = OutputConstraintSet(Seq.empty, Seq.empty, Seq.empty)
    val report = OutputEvaluator.evaluate(artifact, emptyConstraints, now)

    report.decision shouldBe PipelineDecision.Pass
    report.totalCount shouldBe 0
  }

  it should "block when Critical deterministic fails" in {
    val ssnArtifact = OutputArtifact("User SSN: 123-45-6789", Map.empty, None)
    val report = OutputEvaluator.evaluate(ssnArtifact, standardConstraintSet, now)

    report.decision shouldBe a[PipelineDecision.Block]
    val block = report.decision.asInstanceOf[PipelineDecision.Block]
    block.reasons should have size 1
    block.reasons.head.property shouldBe ssnProp
    block.reasons.head.satisfied shouldBe false
  }

  it should "block when Critical composite fails via AnyFlag" in {
    val ssnArtifact = OutputArtifact("PHI data: 123-45-6789", Map.empty, None)
    val report = OutputEvaluator.evaluate(ssnArtifact, criticalConstraintSet, now)

    report.decision shouldBe a[PipelineDecision.Block]
    val block = report.decision.asInstanceOf[PipelineDecision.Block]
    block.reasons.exists(_.property == phiProp) shouldBe true
  }

  it should "flag when Standard classifier fails but no Critical failures" in {
    val highToxicClassifier = FixedScoreClassifier("toxicity_v1", score = 0.9, label = "toxicity")
    val highToxConstraint = ClassifierConstraint(toxicProp, highToxicClassifier, toxicityThreshold, RiskTier.Standard)
    val flagConstraintSet = OutputConstraintSet(deterministic = Seq.empty, classifierBased = Seq(highToxConstraint))

    val artifact = OutputArtifact("Some content", Map.empty, None)
    val report = OutputEvaluator.evaluate(artifact, flagConstraintSet, now)

    report.decision shouldBe a[PipelineDecision.Flag]
    val flag = report.decision.asInstanceOf[PipelineDecision.Flag]
    flag.reasons should have size 1
    flag.reasons.head.property shouldBe toxicProp
  }

  it should "have detected=true/satisfied=false when pattern found" in {
    val ssnArtifact = OutputArtifact("SSN: 123-45-6789", Map.empty, None)
    val report = OutputEvaluator.evaluate(ssnArtifact, standardConstraintSet, now)

    val detResult = report.deterministicResults.find(_.property == ssnProp).get
    detResult.detected shouldBe true
    detResult.satisfied shouldBe false
  }

  it should "have detected=false/satisfied=true when pattern not found" in {
    val cleanArtifact = OutputArtifact("Clean content", Map.empty, None)
    val report = OutputEvaluator.evaluate(cleanArtifact, standardConstraintSet, now)

    val detResult = report.deterministicResults.find(_.property == ssnProp).get
    detResult.detected shouldBe false
    detResult.satisfied shouldBe true
  }

  it should "include score, threshold, thresholdVersion, classifierId, classifierVersion, decision in classifier result" in {
    val artifact = OutputArtifact("Test content", Map.empty, None)
    val report = OutputEvaluator.evaluate(artifact, standardConstraintSet, now)

    val clsResult = report.classifierResults.find(_.property == toxicProp).get
    clsResult.score shouldBe 0.3
    clsResult.threshold shouldBe 0.7
    clsResult.thresholdVersion shouldBe "1.0"
    clsResult.classifierId shouldBe "toxicity_v1"
    clsResult.classifierVersion shouldBe "1.0"
    clsResult.decision shouldBe ThresholdResult.BelowThreshold
  }

  it should "flag classifier when score exceeds threshold" in {
    val highScoreClassifier = FixedScoreClassifier("toxicity_v1", score = 0.9, label = "toxicity")
    val highConstraint = ClassifierConstraint(toxicProp, highScoreClassifier, toxicityThreshold, RiskTier.Standard)
    val constraintSet = OutputConstraintSet(deterministic = Seq.empty, classifierBased = Seq(highConstraint))

    val artifact = OutputArtifact("Test", Map.empty, None)
    val report = OutputEvaluator.evaluate(artifact, constraintSet, now)

    val clsResult = report.classifierResults.head
    clsResult.decision shouldBe ThresholdResult.Flag
    clsResult.satisfied shouldBe false
  }

  it should "fail composite AnyFlag when ANY sub-constraint fails" in {
    val ssnArtifact = OutputArtifact("Contains: 123-45-6789", Map.empty, None)
    val report = OutputEvaluator.evaluate(ssnArtifact, criticalConstraintSet, now)

    val compResult = report.compositeResults.find(_.property == phiProp).get
    compResult.satisfied shouldBe false
    compResult.logic shouldBe CompositeLogic.AnyFlag
    compResult.deterministicResults.exists(!_.satisfied) shouldBe true
  }

  it should "pass composite AllFlag when at least one sub-constraint passes" in {
    val passingDetChecker = NeverDetectsChecker(PropertyId.unsafe("test_prop"))
    val failingClassifier = FixedScoreClassifier("fail_cls", score = 0.95, label = "test")
    val failingThreshold = ThresholdPolicy(0.5, RiskTier.Standard, "test", "1.0")

    val allFlagComposite = CompositeConstraint(
      PropertyId.unsafe("test_composite"),
      Seq(DeterministicConstraint(PropertyId.unsafe("test_prop"), passingDetChecker, RiskTier.Standard)),
      Seq(ClassifierConstraint(PropertyId.unsafe("test_cls_prop"), failingClassifier, failingThreshold, RiskTier.Standard)),
      CompositeLogic.AllFlag,
      RiskTier.Standard
    )

    val constraintSet = OutputConstraintSet(deterministic = Seq.empty, classifierBased = Seq.empty, composite = Seq(allFlagComposite))
    val artifact = OutputArtifact("Test", Map.empty, None)
    val report = OutputEvaluator.evaluate(artifact, constraintSet, now)

    val compResult = report.compositeResults.head
    compResult.satisfied shouldBe true
    compResult.logic shouldBe CompositeLogic.AllFlag
  }

  it should "partition results by type" in {
    val artifact = OutputArtifact("Test content", Map.empty, None)
    val report = OutputEvaluator.evaluate(artifact, standardConstraintSet, now)

    report.deterministicResults should have size 1
    report.classifierResults should have size 1
    report.compositeResults shouldBe empty
    report.results should have size 2
  }

  it should "carry artifact and timestamp in report" in {
    val artifact = OutputArtifact("Test content", Map("key" -> "value"))
    val report = OutputEvaluator.evaluate(artifact, standardConstraintSet, now)

    report.artifact shouldBe artifact
    report.timestamp shouldBe now
  }
