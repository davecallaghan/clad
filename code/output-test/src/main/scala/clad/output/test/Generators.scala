package clad.output.test

import org.scalacheck.{Arbitrary, Gen}
import clad.core.*
import clad.core.test.{Generators => CoreGen}
import clad.output.*

object Generators:
  val genRiskTier: Gen[RiskTier] = Gen.oneOf(RiskTier.values.toSeq)
  given Arbitrary[RiskTier] = Arbitrary(genRiskTier)

  val genOutputArtifact: Gen[OutputArtifact] =
    for content <- Gen.alphaNumStr
    yield OutputArtifact(content)
  given Arbitrary[OutputArtifact] = Arbitrary(genOutputArtifact)

  val genThresholdResult: Gen[ThresholdResult] = Gen.oneOf(ThresholdResult.values.toSeq)
  given Arbitrary[ThresholdResult] = Arbitrary(genThresholdResult)

  val genCompositeLogic: Gen[CompositeLogic] = Gen.oneOf(CompositeLogic.values.toSeq)
  given Arbitrary[CompositeLogic] = Arbitrary(genCompositeLogic)

  val genFallbackAction: Gen[FallbackAction] = Gen.oneOf(FallbackAction.values.toSeq)
  given Arbitrary[FallbackAction] = Arbitrary(genFallbackAction)

  val genDeterministicResult: Gen[DeterministicResult] =
    for
      prop <- CoreGen.genPropertyId
      detected <- Gen.oneOf(true, false)
    yield DeterministicResult(prop, satisfied = !detected, detected = detected)
  given Arbitrary[DeterministicResult] = Arbitrary(genDeterministicResult)

  val genClassifierResult: Gen[ClassifierResult] =
    for
      prop <- CoreGen.genPropertyId
      score <- Gen.choose(0.0, 1.0)
      threshold <- Gen.choose(0.0, 1.0)
    yield
      val decision = if score >= threshold then ThresholdResult.Flag else ThresholdResult.BelowThreshold
      ClassifierResult(prop, satisfied = decision == ThresholdResult.BelowThreshold,
        score, threshold, "1.0", "test_cls", "1.0", decision)
  given Arbitrary[ClassifierResult] = Arbitrary(genClassifierResult)
