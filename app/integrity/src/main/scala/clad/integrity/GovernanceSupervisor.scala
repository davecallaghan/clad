package clad.integrity

import clad.audit.{KeyManagementService, KmsError, Signature}
import java.time.Instant

enum SupervisedResult[+A]:
  case Success(value: A)
  case Failed(degraded: SignedDegradedRecord, action: FailureAction)

class GovernanceSupervisor(
  val supervisorId: ComponentId,
  val kms: KeyManagementService
):
  def supervise[A](
    componentId: ComponentId,
    interactionId: InteractionId,
    posture: FailurePosture,
    evaluate: () => A
  ): SupervisedResult[A] =
    try
      SupervisedResult.Success(evaluate())
    catch
      case e: Exception =>
        val action = FailurePosture.actionFor(posture)
        val degraded = DegradedAuditRecord(
          interactionId = interactionId,
          timestamp = Instant.now(),
          component = componentId,
          status = DegradedStatus.Degraded,
          failureReason = s"${e.getClass.getSimpleName}: ${e.getMessage}",
          postureApplied = posture,
          actionTaken = action
        )
        SignedDegradedRecord.sign(degraded, kms, supervisorId) match
          case Right(signed) => SupervisedResult.Failed(signed, action)
          case Left(_) =>
            val unsigned = SignedDegradedRecord(
              degraded,
              Signature(Array.empty, "unsigned", "none"),
              supervisorId,
              componentId
            )
            SupervisedResult.Failed(unsigned, action)
