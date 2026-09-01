package clad.monitoring

import clad.core.*
import clad.integrity.{InteractionId, ComponentId, FailurePosture, GhostDetector}
import clad.output.{PipelineDecision, FallbackAction}
import java.time.Instant

sealed trait GovernanceEvent:
  def eventId: String
  def timestamp: Instant
  def interactionId: Option[InteractionId]
  def source: ComponentId

case class EvaluationCompleted(
  eventId: String, timestamp: Instant,
  interactionId: Option[InteractionId], source: ComponentId,
  constraintsEvaluated: Int, constraintsSatisfied: Int, allSatisfied: Boolean
) extends GovernanceEvent

case class ViolationDetected(
  eventId: String, timestamp: Instant,
  interactionId: Option[InteractionId], source: ComponentId,
  property: PropertyId, severity: AlertSeverity, detail: String
) extends GovernanceEvent

case class ComponentFailure(
  eventId: String, timestamp: Instant,
  interactionId: Option[InteractionId], source: ComponentId,
  failedComponent: ComponentId, posture: FailurePosture, reason: String
) extends GovernanceEvent

case class GhostDetected(
  eventId: String, timestamp: Instant,
  interactionId: Option[InteractionId], source: ComponentId,
  ghostInteractionId: InteractionId,
  classification: GhostDetector.GhostClassification
) extends GovernanceEvent

case class OutputBlocked(
  eventId: String, timestamp: Instant,
  interactionId: Option[InteractionId], source: ComponentId,
  decision: PipelineDecision.Block, fallbackAction: FallbackAction
) extends GovernanceEvent

case class ConfigurationChange(
  eventId: String, timestamp: Instant,
  interactionId: Option[InteractionId], source: ComponentId,
  changeType: String, description: String
) extends GovernanceEvent
