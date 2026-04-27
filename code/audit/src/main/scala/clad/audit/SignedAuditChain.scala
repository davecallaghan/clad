package clad.audit

import clad.runtime.AuditRecord

case class SignedAuditChain private (records: Vector[SignedAuditRecord]):
  def append(record: AuditRecord, kms: KeyManagementService): Either[KmsError, SignedAuditChain] =
    val linked = record.copy(previousDigest = latest.map(_.record.digest))
    SignedAuditRecord.sign(linked, kms).map { signed =>
      SignedAuditChain(records :+ signed)
    }

  def latest: Option[SignedAuditRecord] = records.lastOption
  def length: Int = records.size

  def isIntegral: Boolean =
    if records.isEmpty then true
    else if records.head.record.previousDigest.isDefined then false
    else records.zip(records.tail).forall { (prev, curr) =>
      curr.record.previousDigest.contains(prev.record.digest)
    }

  def isAuthentic(kms: KeyManagementService): Either[KmsError, Boolean] =
    if records.isEmpty then Right(true)
    else
      val results = records.map(_.isAuthentic(kms))
      val firstError = results.collectFirst { case Left(e) => e }
      firstError match
        case Some(err) => Left(err)
        case None => Right(results.forall(_.exists(_ == true)))

object SignedAuditChain:
  val empty: SignedAuditChain = SignedAuditChain(Vector.empty)
