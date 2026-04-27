package clad.monitoring.test

import clad.core.*
import clad.monitoring.*
import clad.integrity.{InteractionId, ComponentId, FailurePosture, GhostDetector}
import clad.output.{PipelineDecision, FallbackAction}
import java.time.Instant

object TestEvents:
  val defaultTimestamp: Instant = Instant.parse("2026-04-23T12:00:00Z")
  var counter = 0
  def nextId(): String = { counter += 1; s"evt-$counter" }

  def evaluationCompleted(
    satisfied: Boolean = true, total: Int = 5, satisfiedCount: Int = 5,
    ts: Instant = defaultTimestamp, source: ComponentId = ComponentId.Epg
  ): EvaluationCompleted =
    EvaluationCompleted(nextId(), ts, Some(InteractionId("int-1")), source, total, satisfiedCount, satisfied)

  def violationDetected(
    property: String = "phi_detected", severity: AlertSeverity = AlertSeverity.P2High,
    ts: Instant = defaultTimestamp
  ): ViolationDetected =
    ViolationDetected(nextId(), ts, Some(InteractionId("int-1")), ComponentId.Epg,
      PropertyId.unsafe(property), severity, s"Constraint $property violated")

  def componentFailure(
    component: ComponentId = ComponentId.Epg, posture: FailurePosture = FailurePosture.FailClosed,
    ts: Instant = defaultTimestamp
  ): ComponentFailure =
    ComponentFailure(nextId(), ts, Some(InteractionId("int-1")), ComponentId.Supervisor,
      component, posture, "RuntimeException: timeout")

  def ghostDetected(
    ghostId: String = "ghost-1", ts: Instant = defaultTimestamp,
    classification: GhostDetector.GhostClassification = GhostDetector.GhostClassification.Unknown
  ): GhostDetected =
    GhostDetected(nextId(), ts, None, ComponentId.Supervisor, InteractionId(ghostId), classification)

  def outputBlocked(
    ts: Instant = defaultTimestamp, fallback: FallbackAction = FallbackAction.SafeResponseSubstituted
  ): OutputBlocked =
    OutputBlocked(nextId(), ts, Some(InteractionId("int-1")), ComponentId.Roc,
      PipelineDecision.Block(Vector.empty), fallback)

  def configChange(ts: Instant = defaultTimestamp): ConfigurationChange =
    ConfigurationChange(nextId(), ts, None, ComponentId.Supervisor,
      "threshold_change", "PHI classifier threshold lowered from 0.8 to 0.7")
