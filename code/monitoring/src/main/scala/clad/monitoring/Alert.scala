package clad.monitoring

import clad.integrity.InteractionId
import java.time.Instant

enum AlertSeverity:
  case P1Critical
  case P2High
  case P3Medium
  case P4Low

enum AlertCategory:
  case Governance
  case Compliance
  case Adversarial

case class Alert(
  alertId: String,
  timestamp: Instant,
  category: AlertCategory,
  severity: AlertSeverity,
  title: String,
  detail: String,
  sourceEvent: GovernanceEvent,
  detectorId: String,
  interactionId: Option[InteractionId] = None,
  metadata: Map[String, String] = Map.empty
)

trait AlertSink:
  def emit(alert: Alert): Unit

class InMemoryAlertSink extends AlertSink:
  private var alerts: Vector[Alert] = Vector.empty
  def emit(alert: Alert): Unit = alerts = alerts :+ alert
  def allAlerts: Vector[Alert] = alerts
  def alertsByCategory(cat: AlertCategory): Vector[Alert] = alerts.filter(_.category == cat)
  def alertsBySeverity(sev: AlertSeverity): Vector[Alert] = alerts.filter(_.severity == sev)
  def clear(): Unit = alerts = Vector.empty
