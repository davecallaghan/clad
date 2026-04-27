package clad.integrity

import clad.runtime.{GovernanceEngine, GovernanceReport}
import clad.audit.Signature
import java.time.Instant
import scala.util.Try

case class SupervisedEvaluation(
  interactionId: InteractionId,
  result: SupervisedResult[Either[GovernanceEngine.EvaluationFailure, GovernanceReport]],
  gilRegistered: Boolean
)

class SupervisedEngine(
  engine: GovernanceEngine,
  supervisor: GovernanceSupervisor,
  gil: InteractionLog[Try],
  posture: FailurePosture
):
  def evaluate(
    prompt: String,
    promptMeta: Map[String, String] = Map.empty
  ): SupervisedEvaluation =
    val interactionId = InteractionId.generate()
    val now = Instant.now()

    val gilRegistered = gil.register(GilEntry(interactionId, now)) match
      case scala.util.Success(_) => true
      case scala.util.Failure(e) =>
        if posture == FailurePosture.FailClosed then
          val degraded = DegradedAuditRecord(
            interactionId, now, ComponentId("gil"), DegradedStatus.Degraded,
            s"GIL registration failed: ${e.getMessage}",
            posture, FailureAction.Blocked
          )
          val signed = SignedDegradedRecord.sign(degraded, supervisor.kms, supervisor.supervisorId)
            .getOrElse(SignedDegradedRecord(degraded,
              Signature(Array.empty, "unsigned", "none"),
              supervisor.supervisorId, ComponentId("gil")))
          return SupervisedEvaluation(
            interactionId,
            SupervisedResult.Failed(signed, FailureAction.Blocked),
            gilRegistered = false
          )
        false

    val result = supervisor.supervise(
      ComponentId.Epg, interactionId, posture,
      () => engine.evaluate(prompt, promptMeta)
    )

    SupervisedEvaluation(interactionId, result, gilRegistered)
