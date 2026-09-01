package clad.output.test

import clad.core.*
import clad.output.*

object SampleOutputConstraints:
  val ssnProp: PropertyId = PropertyId.unsafe("ssn_detected")
  val phiProp: PropertyId = PropertyId.unsafe("phi_detected")
  val toxicProp: PropertyId = PropertyId.unsafe("toxicity_detected")

  val ssnChecker: OutputChecker = RegexOutputChecker(ssnProp, "\\d{3}-\\d{2}-\\d{4}".r)
  val ssnConstraint: DeterministicConstraint = DeterministicConstraint(ssnProp, ssnChecker, RiskTier.Critical)

  val toxicityClassifier: OutputClassifier = FixedScoreClassifier("toxicity_v1", score = 0.3, label = "toxicity")
  val toxicityThreshold: ThresholdPolicy = ThresholdPolicy(0.7, RiskTier.Standard, "safety_team", "1.0")
  val toxicityConstraint: ClassifierConstraint = ClassifierConstraint(toxicProp, toxicityClassifier, toxicityThreshold, RiskTier.Standard)

  val phiChecker: OutputChecker = RegexOutputChecker(phiProp, "\\d{3}-\\d{2}-\\d{4}".r)
  val phiClassifier: OutputClassifier = FixedScoreClassifier("phi_ner_v1", score = 0.85, label = "phi_likelihood")
  val phiThreshold: ThresholdPolicy = ThresholdPolicy(0.5, RiskTier.Critical, "compliance", "1.0")
  val phiComposite: CompositeConstraint = CompositeConstraint(
    phiProp,
    Seq(DeterministicConstraint(phiProp, phiChecker, RiskTier.Critical)),
    Seq(ClassifierConstraint(phiProp, phiClassifier, phiThreshold, RiskTier.Critical)),
    CompositeLogic.AnyFlag, RiskTier.Critical
  )

  val standardConstraintSet: OutputConstraintSet = OutputConstraintSet(
    deterministic = Seq(ssnConstraint), classifierBased = Seq(toxicityConstraint))

  val criticalConstraintSet: OutputConstraintSet = OutputConstraintSet(
    deterministic = Seq(ssnConstraint), composite = Seq(phiComposite))
