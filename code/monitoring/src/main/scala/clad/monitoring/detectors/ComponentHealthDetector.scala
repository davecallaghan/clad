package clad.monitoring.detectors

import clad.monitoring.*
import clad.integrity.FailurePosture

class ComponentHealthDetector extends EventDetector:
  val detectorId = "component_health"
  def detect(event: GovernanceEvent): Vector[Alert] = event match
    case cf: ComponentFailure =>
      val severity = cf.posture match
        case FailurePosture.FailClosed => AlertSeverity.P2High
        case FailurePosture.FailOpenFlagged => AlertSeverity.P3Medium
        case FailurePosture.FailOpen => AlertSeverity.P3Medium
      Vector(Alert(s"alert-${cf.eventId}", cf.timestamp, AlertCategory.Governance, severity,
        s"Component ${cf.failedComponent.value} failed",
        s"Component ${cf.failedComponent.value} failed with posture ${cf.posture}. Reason: ${cf.reason}",
        cf, detectorId, cf.interactionId))
    case _ => Vector.empty
