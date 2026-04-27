package clad.monitoring.detectors

import clad.monitoring.*
import java.time.Duration

class GhostRateDetector(
  windowDuration: Duration = Duration.ofMinutes(15),
  threshold: Double = 0.05
) extends AbstractWindowDetector("ghost_rate", windowDuration):
  protected def evaluate(events: Vector[GovernanceEvent]): Vector[Alert] =
    val ghosts = events.collect { case g: GhostDetected => g }
    val evaluations = events.collect { case e: EvaluationCompleted => e }
    val totalInteractions = evaluations.size + ghosts.size
    if totalInteractions == 0 then return Vector.empty
    val ghostRate = ghosts.size.toDouble / totalInteractions
    if ghostRate > threshold then
      val triggerEvent = ghosts.lastOption.getOrElse(events.last)
      Vector(Alert(s"alert-ghost-rate-${triggerEvent.eventId}", triggerEvent.timestamp,
        AlertCategory.Governance, AlertSeverity.P2High,
        s"Ghost rate ${(ghostRate * 100).round}% exceeds threshold ${(threshold * 100).round}%",
        s"${ghosts.size} ghosts detected in $totalInteractions interactions within window",
        triggerEvent, detectorId, metadata = Map("ghost_rate" -> f"$ghostRate%.4f", "ghost_count" -> ghosts.size.toString)))
    else Vector.empty
