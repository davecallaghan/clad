package clad.mdr

import clad.monitoring.{AlertCategory, AlertSeverity}
import java.time.Instant

/** One MDR alert. `dedupeKey` makes a repeat impossible across cycles and restarts (a unique key in mdr.alert). */
final case class MdrAlert(dedupeKey: String, severity: AlertSeverity, category: AlertCategory, detector: String,
                          interactionIds: Vector[String], summary: String, project: Option[String]):
  def severityLabel: String = MdrConfig.Labels(severity)
  def taxonomy: String = category.toString.toLowerCase

/** A detector reads one snapshot. It must filter its candidates against `s.alertedKeys` itself, so it never returns a
  * key already written. A Left is a detector failure: the cycle still writes its heartbeat and alerts about it. */
trait Detector:
  def id: String
  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]]
