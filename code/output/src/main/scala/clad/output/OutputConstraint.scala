package clad.output

import clad.core.*

enum RiskTier:
  case Critical, Standard, Low

trait OutputChecker:
  def propertyId: PropertyId
  def check(artifact: OutputArtifact): Boolean

sealed trait OutputConstraint:
  def property: PropertyId
  def riskTier: RiskTier

case class DeterministicConstraint(
  property: PropertyId,
  checker: OutputChecker,
  riskTier: RiskTier
) extends OutputConstraint

case class ClassifierConstraint(
  property: PropertyId,
  classifier: OutputClassifier,
  threshold: ThresholdPolicy,
  riskTier: RiskTier
) extends OutputConstraint

enum CompositeLogic:
  case AnyFlag, AllFlag

case class CompositeConstraint(
  property: PropertyId,
  deterministic: Seq[DeterministicConstraint],
  classifierBased: Seq[ClassifierConstraint],
  logic: CompositeLogic,
  riskTier: RiskTier
) extends OutputConstraint

case class OutputConstraintSet(
  deterministic: Seq[DeterministicConstraint] = Seq.empty,
  classifierBased: Seq[ClassifierConstraint] = Seq.empty,
  composite: Seq[CompositeConstraint] = Seq.empty
)
