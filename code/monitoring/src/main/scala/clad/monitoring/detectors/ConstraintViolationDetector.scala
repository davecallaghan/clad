package clad.monitoring.detectors

import clad.monitoring.*

class ConstraintViolationDetector extends EventDetector:
  val detectorId = "constraint_violation"
  def detect(event: GovernanceEvent): Vector[Alert] = event match
    case v: ViolationDetected =>
      Vector(Alert(s"alert-${v.eventId}", v.timestamp, AlertCategory.Compliance, v.severity,
        s"Constraint violation: ${v.property.value}", v.detail, v, detectorId, v.interactionId))
    case _ => Vector.empty
