package clad.monitoring.detectors

import clad.monitoring.*
import clad.integrity.ComponentId
import java.time.Duration

class BlockRateAnomalyDetector(
  windowDuration: Duration = Duration.ofMinutes(15),
  threshold: Double = 0.20
) extends AbstractWindowDetector("block_rate_anomaly", windowDuration):
  protected def evaluate(events: Vector[GovernanceEvent]): Vector[Alert] =
    val blocks = events.collect { case b: OutputBlocked => b }
    val evaluations = events.collect { case e: EvaluationCompleted if e.source == ComponentId.Roc => e }
    val totalOutputEvals = evaluations.size + blocks.size
    if totalOutputEvals == 0 then return Vector.empty
    val blockRate = blocks.size.toDouble / totalOutputEvals
    if blockRate > threshold then
      val triggerEvent = blocks.lastOption.getOrElse(events.last)
      Vector(Alert(s"alert-block-rate-${triggerEvent.eventId}", triggerEvent.timestamp,
        AlertCategory.Compliance, AlertSeverity.P3Medium,
        s"Output block rate ${(blockRate * 100).round}% exceeds threshold ${(threshold * 100).round}%",
        s"${blocks.size} outputs blocked in $totalOutputEvals evaluations within window",
        triggerEvent, detectorId, metadata = Map("block_rate" -> f"$blockRate%.4f", "block_count" -> blocks.size.toString)))
    else Vector.empty
