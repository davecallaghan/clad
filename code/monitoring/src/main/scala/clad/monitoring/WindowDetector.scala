package clad.monitoring

import java.time.Duration

trait WindowDetector:
  def detectorId: String
  def windowSize: Duration
  def ingest(event: GovernanceEvent): Vector[Alert]
  def currentWindow: Vector[GovernanceEvent]

abstract class AbstractWindowDetector(
  val detectorId: String,
  val windowSize: Duration
) extends WindowDetector:
  private var window: Vector[GovernanceEvent] = Vector.empty
  def currentWindow: Vector[GovernanceEvent] = window
  def ingest(event: GovernanceEvent): Vector[Alert] =
    window = (window :+ event).filter { e =>
      !e.timestamp.isBefore(event.timestamp.minus(windowSize))
    }
    evaluate(window)
  protected def evaluate(events: Vector[GovernanceEvent]): Vector[Alert]
