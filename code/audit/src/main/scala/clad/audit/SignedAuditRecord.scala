package clad.audit

import clad.runtime.AuditRecord

case class SignedAuditRecord(
  record: AuditRecord,
  signature: Signature
):
  def isAuthentic(kms: KeyManagementService): Either[KmsError, Boolean] =
    kms.verify(record.digest.getBytes("UTF-8"), signature)

object SignedAuditRecord:
  def sign(record: AuditRecord, kms: KeyManagementService): Either[KmsError, SignedAuditRecord] =
    kms.sign(record.digest.getBytes("UTF-8")).map(sig => SignedAuditRecord(record, sig))
