package clad.output

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.output.test.*

class OutputConstraintSpec extends AnyFlatSpec with Matchers:
  val testProp: PropertyId = PropertyId.unsafe("test_output_property")

  "OutputArtifact" should "store content and metadata" in {
    val artifact = OutputArtifact("test content", Map("key" -> "value"))
    artifact.content shouldBe "test content"
    artifact.metadata shouldBe Map("key" -> "value")
    artifact.promptContext shouldBe None
  }

  it should "accept optional PromptContext" in {
    val context = PromptContext("digest123", Set(testProp))
    val artifact = OutputArtifact("content", promptContext = Some(context))
    artifact.promptContext shouldBe Some(context)
    artifact.promptContext.get.promptDigest shouldBe "digest123"
    artifact.promptContext.get.constraintProperties shouldBe Set(testProp)
  }

  "RiskTier" should "have 3 values" in {
    RiskTier.values.length shouldBe 3
    RiskTier.values should contain allOf (RiskTier.Critical, RiskTier.Standard, RiskTier.Low)
  }

  "AlwaysDetectsChecker" should "always return true" in {
    val checker = AlwaysDetectsChecker(testProp)
    val artifact = OutputArtifact("any content")
    checker.check(artifact) shouldBe true
    checker.propertyId shouldBe testProp
  }

  "NeverDetectsChecker" should "always return false" in {
    val checker = NeverDetectsChecker(testProp)
    val artifact = OutputArtifact("any content")
    checker.check(artifact) shouldBe false
    checker.propertyId shouldBe testProp
  }

  "KeywordOutputChecker" should "detect keyword case-insensitively" in {
    val checker = KeywordOutputChecker(testProp, "secret")
    checker.check(OutputArtifact("contains SECRET word")) shouldBe true
    checker.check(OutputArtifact("contains secret word")) shouldBe true
    checker.check(OutputArtifact("no sensitive word")) shouldBe false
  }

  "RegexOutputChecker" should "detect pattern matches" in {
    val checker = RegexOutputChecker(testProp, "\\d{3}-\\d{2}-\\d{4}".r)
    checker.check(OutputArtifact("SSN: 123-45-6789")) shouldBe true
    checker.check(OutputArtifact("no pattern here")) shouldBe false
  }

  "ThresholdPolicy" should "flag scores at or above threshold" in {
    val policy = ThresholdPolicy(0.8, RiskTier.Critical, "security-team", "1.0")
    policy.evaluate(0.8) shouldBe ThresholdResult.Flag
    policy.evaluate(0.9) shouldBe ThresholdResult.Flag
    policy.evaluate(1.0) shouldBe ThresholdResult.Flag
  }

  it should "return BelowThreshold for scores below threshold" in {
    val policy = ThresholdPolicy(0.8, RiskTier.Critical, "security-team", "1.0")
    policy.evaluate(0.79) shouldBe ThresholdResult.BelowThreshold
    policy.evaluate(0.5) shouldBe ThresholdResult.BelowThreshold
    policy.evaluate(0.0) shouldBe ThresholdResult.BelowThreshold
  }

  "AlwaysFlags" should "return score of 1.0" in {
    val classifier = AlwaysFlags("always-clf", "1.0", "flag")
    val result = classifier.classify(OutputArtifact("any content"))
    result.score shouldBe 1.0
    result.classifierId shouldBe "always-clf"
    result.version shouldBe "1.0"
    result.label shouldBe "flag"
  }

  "NeverFlags" should "return score of 0.0" in {
    val classifier = NeverFlags("never-clf", "1.0", "safe")
    val result = classifier.classify(OutputArtifact("any content"))
    result.score shouldBe 0.0
    result.classifierId shouldBe "never-clf"
    result.version shouldBe "1.0"
    result.label shouldBe "safe"
  }

  "FixedScoreClassifier" should "return specified score" in {
    val classifier = FixedScoreClassifier("fixed-clf", "1.0", 0.75, "medium")
    val result = classifier.classify(OutputArtifact("any content"))
    result.score shouldBe 0.75
    result.classifierId shouldBe "fixed-clf"
    result.version shouldBe "1.0"
    result.label shouldBe "medium"
  }

  "DeterministicConstraint" should "hold property, checker, and riskTier" in {
    val checker = AlwaysDetectsChecker(testProp)
    val constraint = DeterministicConstraint(testProp, checker, RiskTier.Critical)
    constraint.property shouldBe testProp
    constraint.checker shouldBe checker
    constraint.riskTier shouldBe RiskTier.Critical
  }

  "ClassifierConstraint" should "hold property, classifier, threshold, and riskTier" in {
    val classifier = AlwaysFlags("test-clf")
    val threshold = ThresholdPolicy(0.8, RiskTier.Standard, "owner", "1.0")
    val constraint = ClassifierConstraint(testProp, classifier, threshold, RiskTier.Standard)
    constraint.property shouldBe testProp
    constraint.classifier shouldBe classifier
    constraint.threshold shouldBe threshold
    constraint.riskTier shouldBe RiskTier.Standard
  }

  "CompositeConstraint" should "hold property, constraints, logic, and riskTier" in {
    val checker = AlwaysDetectsChecker(testProp)
    val detConstraint = DeterministicConstraint(testProp, checker, RiskTier.Low)
    val classifier = NeverFlags("test-clf")
    val threshold = ThresholdPolicy(0.5, RiskTier.Low, "owner", "1.0")
    val clfConstraint = ClassifierConstraint(testProp, classifier, threshold, RiskTier.Low)

    val composite = CompositeConstraint(
      testProp,
      Seq(detConstraint),
      Seq(clfConstraint),
      CompositeLogic.AnyFlag,
      RiskTier.Low
    )

    composite.property shouldBe testProp
    composite.deterministic should contain (detConstraint)
    composite.classifierBased should contain (clfConstraint)
    composite.logic shouldBe CompositeLogic.AnyFlag
    composite.riskTier shouldBe RiskTier.Low
  }

  "OutputConstraintSet" should "default to empty sequences" in {
    val emptySet = OutputConstraintSet()
    emptySet.deterministic shouldBe empty
    emptySet.classifierBased shouldBe empty
    emptySet.composite shouldBe empty
  }

  it should "hold provided constraints" in {
    val checker = AlwaysDetectsChecker(testProp)
    val detConstraint = DeterministicConstraint(testProp, checker, RiskTier.Critical)
    val classifier = AlwaysFlags("test-clf")
    val threshold = ThresholdPolicy(0.8, RiskTier.Standard, "owner", "1.0")
    val clfConstraint = ClassifierConstraint(testProp, classifier, threshold, RiskTier.Standard)

    val constraintSet = OutputConstraintSet(
      deterministic = Seq(detConstraint),
      classifierBased = Seq(clfConstraint)
    )

    constraintSet.deterministic should contain (detConstraint)
    constraintSet.classifierBased should contain (clfConstraint)
    constraintSet.composite shouldBe empty
  }
