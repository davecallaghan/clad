package clad.audit

import clad.runtime.AuditRecord

/** A signed audit record, carrying the digest as it was written.
  *
  * `recordedDigest` is not redundant with `record.digest`: the latter is a
  * `lazy val` recomputed from the record's own fields, so comparing it against a
  * fresh recomputation is vacuous. Persisting the digest gives verification
  * something independent to disagree with. `None` means the record predates this
  * field and its content cannot be checked by recomputation.
  */
case class SignedAuditRecord(
  record: AuditRecord,
  signature: Signature,
  recordedDigest: Option[String]
):
  def isAuthentic(kms: KeyManagementService): Either[KmsError, Boolean] =
    kms.verify(record.digest.getBytes("UTF-8"), signature)

object SignedAuditRecord:
  def sign(record: AuditRecord, kms: KeyManagementService): Either[KmsError, SignedAuditRecord] =
    kms.sign(record.digest.getBytes("UTF-8"))
      .map(sig => SignedAuditRecord(record, sig, Some(record.digest)))
