package clad.monitoring

trait EventDetector:
  def detectorId: String
  def detect(event: GovernanceEvent): Vector[Alert]
