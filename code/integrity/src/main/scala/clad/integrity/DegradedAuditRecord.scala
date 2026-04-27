package clad.integrity

import clad.audit.{Signature, KeyManagementService, KmsError}
import java.time.Instant

enum DegradedStatus:
  case Degraded

case class DegradedAuditRecord(
  interactionId: InteractionId,
  timestamp: Instant,
  component: ComponentId,
  status: DegradedStatus,
  failureReason: String,
  postureApplied: FailurePosture,
  actionTaken: FailureAction
)

case class SignedDegradedRecord(
  record: DegradedAuditRecord,
  signature: Signature,
  signedBy: ComponentId,
  onBehalfOf: ComponentId
)

object SignedDegradedRecord:
  def sign(
    record: DegradedAuditRecord,
    kms: KeyManagementService,
    supervisorId: ComponentId
  ): Either[KmsError, SignedDegradedRecord] =
    val data = s"${record.interactionId.value}|${record.timestamp}|${record.component.value}|${record.failureReason}|${record.postureApplied}|${record.actionTaken}"
    kms.sign(data.getBytes("UTF-8")).map { sig =>
      SignedDegradedRecord(record, sig, signedBy = supervisorId, onBehalfOf = record.component)
    }
