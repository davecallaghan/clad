package clad.audit

import clad.runtime.AuditRecord
import java.security.MessageDigest

object AuditVerifier:

  case class VerificationReport(
    recordCount: Int,
    chainIntegral: Boolean,
    allSignaturesValid: Boolean,
    firstFailureAt: Option[Int],
    failures: Vector[VerificationFailure]
  ):
    def isFullyVerified: Boolean = chainIntegral && allSignaturesValid && failures.isEmpty

  sealed trait VerificationFailure
  case class BrokenChainLink(index: Int, expected: String, actual: Option[String]) extends VerificationFailure
  case class InvalidSignature(index: Int, keyId: String) extends VerificationFailure
  case class DigestMismatch(index: Int, recorded: String, recomputed: String) extends VerificationFailure

  def verify(
    records: Vector[SignedAuditRecord],
    kms: KeyManagementService
  ): VerificationReport =
    if records.isEmpty then
      return VerificationReport(0, true, true, None, Vector.empty)

    val failures = Vector.newBuilder[VerificationFailure]
    var chainOk = true
    var sigsOk = true
    var firstFailure: Option[Int] = None

    def recordFailure(idx: Int, failure: VerificationFailure): Unit =
      failures += failure
      if firstFailure.isEmpty then firstFailure = Some(idx)

    records.zipWithIndex.foreach { (signed, idx) =>
      val record = signed.record

      // Check 1: Digest recomputation
      val recomputed = recomputeDigest(record)
      if recomputed != record.digest then
        recordFailure(idx, DigestMismatch(idx, record.digest, recomputed))
        chainOk = false

      // Check 2: Chain link
      if idx == 0 then
        if record.previousDigest.isDefined then
          recordFailure(idx, BrokenChainLink(idx, "None", record.previousDigest))
          chainOk = false
      else
        val expectedPrev = records(idx - 1).record.digest
        if !record.previousDigest.contains(expectedPrev) then
          recordFailure(idx, BrokenChainLink(idx, expectedPrev, record.previousDigest))
          chainOk = false

      // Check 3: Signature
      kms.verify(record.digest.getBytes("UTF-8"), signed.signature) match
        case Right(true) => // ok
        case Right(false) =>
          recordFailure(idx, InvalidSignature(idx, signed.signature.keyId))
          sigsOk = false
        case Left(_) =>
          recordFailure(idx, InvalidSignature(idx, signed.signature.keyId))
          sigsOk = false
    }

    VerificationReport(records.size, chainOk, sigsOk, firstFailure, failures.result())

  def verifyStore[F[_]](
    store: AuditStore[F],
    kms: KeyManagementService
  )(using lift: Lift[F]): F[VerificationReport] =
    lift.fromTry(scala.util.Try {
      val records = store.readAll match
        case t: scala.util.Try[?] => t.asInstanceOf[scala.util.Try[Vector[SignedAuditRecord]]].get
        case other => throw RuntimeException(s"Unsupported effect type")
      verify(records, kms)
    })

  private def recomputeDigest(record: AuditRecord): String =
    val canonical = s"${record.artifactDigest}|${record.configDigest}|${record.timestamp}|${record.previousDigest}|" +
      record.entries.map(e =>
        s"${e.constraint.property.value}:${e.constraintVersion}:${e.evaluabilityClass}:${e.satisfied}"
      ).sorted.mkString("|")
    val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes("UTF-8"))
    "sha256:" + bytes.map("%02x".format(_)).mkString
