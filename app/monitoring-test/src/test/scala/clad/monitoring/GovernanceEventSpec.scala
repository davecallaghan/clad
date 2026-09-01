package clad.monitoring

import clad.core.*
import clad.integrity.{InteractionId, ComponentId, FailurePosture, GhostDetector}
import clad.output.{PipelineDecision, FallbackAction}
import clad.monitoring.test.TestEvents
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class GovernanceEventSpec extends AnyFlatSpec with Matchers:

  "EvaluationCompleted" should "carry all fields correctly" in {
    val event = TestEvents.evaluationCompleted(satisfied = true, total = 10, satisfiedCount = 10)
    event.eventId should startWith("evt-")
    event.timestamp shouldBe TestEvents.defaultTimestamp
    event.interactionId shouldBe Some(InteractionId("int-1"))
    event.source shouldBe ComponentId.Epg
    event.constraintsEvaluated shouldBe 10
    event.constraintsSatisfied shouldBe 10
    event.allSatisfied shouldBe true
  }

  "ViolationDetected" should "carry property, severity, and detail" in {
    val event = TestEvents.violationDetected(property = "phi_detected", severity = AlertSeverity.P1Critical)
    event.eventId should startWith("evt-")
    event.timestamp shouldBe TestEvents.defaultTimestamp
    event.interactionId shouldBe Some(InteractionId("int-1"))
    event.source shouldBe ComponentId.Epg
    event.property shouldBe PropertyId.unsafe("phi_detected")
    event.severity shouldBe AlertSeverity.P1Critical
    event.detail should include("phi_detected")
  }

  "ComponentFailure" should "carry failed component, posture, and reason" in {
    val event = TestEvents.componentFailure(component = ComponentId.Roc, posture = FailurePosture.FailOpen)
    event.eventId should startWith("evt-")
    event.timestamp shouldBe TestEvents.defaultTimestamp
    event.interactionId shouldBe Some(InteractionId("int-1"))
    event.source shouldBe ComponentId.Supervisor
    event.failedComponent shouldBe ComponentId.Roc
    event.posture shouldBe FailurePosture.FailOpen
    event.reason should include("RuntimeException")
  }

  "GhostDetected" should "carry ghost interaction ID and classification" in {
    val event = TestEvents.ghostDetected(ghostId = "ghost-123", classification = GhostDetector.GhostClassification.ComponentFailure)
    event.eventId should startWith("evt-")
    event.timestamp shouldBe TestEvents.defaultTimestamp
    event.interactionId shouldBe None
    event.source shouldBe ComponentId.Supervisor
    event.ghostInteractionId shouldBe InteractionId("ghost-123")
    event.classification shouldBe GhostDetector.GhostClassification.ComponentFailure
  }

  "OutputBlocked" should "carry decision and fallback action" in {
    val event = TestEvents.outputBlocked(fallback = FallbackAction.Redacted)
    event.eventId should startWith("evt-")
    event.timestamp shouldBe TestEvents.defaultTimestamp
    event.interactionId shouldBe Some(InteractionId("int-1"))
    event.source shouldBe ComponentId.Roc
    event.decision shouldBe a[PipelineDecision.Block]
    event.fallbackAction shouldBe FallbackAction.Redacted
  }

  "ConfigurationChange" should "carry change type and description" in {
    val event = TestEvents.configChange()
    event.eventId should startWith("evt-")
    event.timestamp shouldBe TestEvents.defaultTimestamp
    event.interactionId shouldBe None
    event.source shouldBe ComponentId.Supervisor
    event.changeType shouldBe "threshold_change"
    event.description should include("PHI classifier")
  }

  "AlertSeverity" should "have 4 values" in {
    val values = AlertSeverity.values
    values should have length 4
    values should contain allOf (
      AlertSeverity.P1Critical,
      AlertSeverity.P2High,
      AlertSeverity.P3Medium,
      AlertSeverity.P4Low
    )
  }

  "AlertCategory" should "have 3 values" in {
    val values = AlertCategory.values
    values should have length 3
    values should contain allOf (
      AlertCategory.Governance,
      AlertCategory.Compliance,
      AlertCategory.Adversarial
    )
  }

  "Alert" should "carry all fields including metadata" in {
    val event = TestEvents.violationDetected()
    val alert = Alert(
      alertId = "alert-1",
      timestamp = Instant.now(),
      category = AlertCategory.Compliance,
      severity = AlertSeverity.P2High,
      title = "PHI Detected",
      detail = "PHI found in output",
      sourceEvent = event,
      detectorId = "phi-detector",
      interactionId = Some(InteractionId("int-1")),
      metadata = Map("region" -> "us-east-1", "env" -> "prod")
    )

    alert.alertId shouldBe "alert-1"
    alert.category shouldBe AlertCategory.Compliance
    alert.severity shouldBe AlertSeverity.P2High
    alert.title shouldBe "PHI Detected"
    alert.detail shouldBe "PHI found in output"
    alert.sourceEvent shouldBe event
    alert.detectorId shouldBe "phi-detector"
    alert.interactionId shouldBe Some(InteractionId("int-1"))
    alert.metadata shouldBe Map("region" -> "us-east-1", "env" -> "prod")
  }

  "InMemoryAlertSink" should "collect alerts" in {
    val sink = new InMemoryAlertSink
    val event1 = TestEvents.violationDetected()
    val event2 = TestEvents.componentFailure()

    val alert1 = Alert("a1", Instant.now(), AlertCategory.Governance, AlertSeverity.P2High,
      "Violation", "detail", event1, "det-1")
    val alert2 = Alert("a2", Instant.now(), AlertCategory.Compliance, AlertSeverity.P1Critical,
      "Failure", "detail", event2, "det-2")

    sink.emit(alert1)
    sink.emit(alert2)

    sink.allAlerts should have length 2
    sink.allAlerts should contain allOf (alert1, alert2)
  }

  it should "filter by category" in {
    val sink = new InMemoryAlertSink
    val event1 = TestEvents.violationDetected()
    val event2 = TestEvents.componentFailure()

    val alert1 = Alert("a1", Instant.now(), AlertCategory.Governance, AlertSeverity.P2High,
      "Violation", "detail", event1, "det-1")
    val alert2 = Alert("a2", Instant.now(), AlertCategory.Compliance, AlertSeverity.P1Critical,
      "Failure", "detail", event2, "det-2")
    val alert3 = Alert("a3", Instant.now(), AlertCategory.Governance, AlertSeverity.P3Medium,
      "Another", "detail", event1, "det-3")

    sink.emit(alert1)
    sink.emit(alert2)
    sink.emit(alert3)

    val governanceAlerts = sink.alertsByCategory(AlertCategory.Governance)
    governanceAlerts should have length 2
    governanceAlerts should contain allOf (alert1, alert3)

    val complianceAlerts = sink.alertsByCategory(AlertCategory.Compliance)
    complianceAlerts should have length 1
    complianceAlerts should contain(alert2)
  }

  it should "filter by severity" in {
    val sink = new InMemoryAlertSink
    val event = TestEvents.violationDetected()

    val alert1 = Alert("a1", Instant.now(), AlertCategory.Governance, AlertSeverity.P1Critical,
      "Critical", "detail", event, "det-1")
    val alert2 = Alert("a2", Instant.now(), AlertCategory.Compliance, AlertSeverity.P2High,
      "High", "detail", event, "det-2")
    val alert3 = Alert("a3", Instant.now(), AlertCategory.Governance, AlertSeverity.P1Critical,
      "Another Critical", "detail", event, "det-3")

    sink.emit(alert1)
    sink.emit(alert2)
    sink.emit(alert3)

    val criticalAlerts = sink.alertsBySeverity(AlertSeverity.P1Critical)
    criticalAlerts should have length 2
    criticalAlerts should contain allOf (alert1, alert3)

    val highAlerts = sink.alertsBySeverity(AlertSeverity.P2High)
    highAlerts should have length 1
    highAlerts should contain(alert2)
  }

  it should "clear all alerts" in {
    val sink = new InMemoryAlertSink
    val event = TestEvents.violationDetected()
    val alert = Alert("a1", Instant.now(), AlertCategory.Governance, AlertSeverity.P2High,
      "Test", "detail", event, "det-1")

    sink.emit(alert)
    sink.allAlerts should have length 1

    sink.clear()
    sink.allAlerts should have length 0
  }
