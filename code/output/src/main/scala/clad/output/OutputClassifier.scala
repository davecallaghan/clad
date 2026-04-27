package clad.output

case class ClassificationResult(
  classifierId: String,
  version: String,
  score: Double,
  label: String
)

trait OutputClassifier:
  def classifierId: String
  def version: String
  def classify(artifact: OutputArtifact): ClassificationResult

enum ThresholdResult:
  case Flag, BelowThreshold

case class ThresholdPolicy(
  flagThreshold: Double,
  riskTier: RiskTier,
  owner: String,
  version: String
):
  def evaluate(score: Double): ThresholdResult =
    if score >= flagThreshold then ThresholdResult.Flag
    else ThresholdResult.BelowThreshold
