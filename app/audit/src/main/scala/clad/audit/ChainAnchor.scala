package clad.audit

import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.Base64
import scala.util.Try
import upickle.default.*

/** The chain's length and head digest, recorded outside the chain.
  *
  * AuditVerifier.verify can only check the records it is handed, so a chain whose
  * tail has been deleted verifies cleanly: every surviving link matches its
  * predecessor and every surviving signature is valid. The anchor is what makes
  * the absence visible.
  *
  * Scope: the anchor is signed, so forging it requires the KMS key, and it is
  * written under the same lock as the append so it cannot drift from the chain. An
  * attacker holding the signing key can still rewrite both consistently. This
  * raises the cost of truncation; it does not make the chain unforgeable.
  *
  * Implements AI5 (Chain Truncation Detection).
  */
case class ChainAnchor(count: Int, headDigest: String, updatedAt: Instant):
  def canonical: String = s"$count|$headDigest|$updatedAt"

case class SignedChainAnchor(anchor: ChainAnchor, signature: Signature)

object ChainAnchor:
  val Genesis: String = "sha256:genesis"

  def forChain(records: Vector[SignedAuditRecord], now: Instant): ChainAnchor =
    ChainAnchor(
      count = records.size,
      headDigest = records.lastOption.map(_.record.digest).getOrElse(Genesis),
      updatedAt = now
    )

  def sign(anchor: ChainAnchor, kms: KeyManagementService): Either[KmsError, SignedChainAnchor] =
    kms.sign(anchor.canonical.getBytes("UTF-8")).map(SignedChainAnchor(anchor, _))

  def isAuthentic(signed: SignedChainAnchor, kms: KeyManagementService): Boolean =
    kms.verify(signed.anchor.canonical.getBytes("UTF-8"), signed.signature) match
      case Right(ok) => ok
      case Left(_)   => false

class ChainAnchorStore(directory: Path):
  private val anchorFile = directory.resolve("anchor.json")

  private given ReadWriter[Instant] = readwriter[String].bimap(_.toString, Instant.parse(_))

  private given ReadWriter[SignedChainAnchor] = readwriter[ujson.Value].bimap(
    s => ujson.Obj(
      "count" -> s.anchor.count,
      "headDigest" -> s.anchor.headDigest,
      "updatedAt" -> writeJs(s.anchor.updatedAt),
      "signature" -> ujson.Obj(
        "value" -> Base64.getEncoder.encodeToString(s.signature.value),
        "keyId" -> s.signature.keyId,
        "algorithm" -> s.signature.algorithm
      )
    ),
    json => SignedChainAnchor(
      ChainAnchor(json("count").num.toInt, json("headDigest").str,
        upickle.default.read[Instant](json("updatedAt"))),
      Signature(
        Base64.getDecoder.decode(json("signature")("value").str),
        json("signature")("keyId").str,
        json("signature")("algorithm").str
      )
    )
  )

  def write(signed: SignedChainAnchor): Try[Unit] = Try {
    if !Files.exists(directory) then Files.createDirectories(directory)
    Files.write(
      anchorFile,
      upickle.default.write(signed).getBytes("UTF-8"),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    ()
  }

  def read: Try[Option[SignedChainAnchor]] = Try {
    if !Files.exists(anchorFile) then None
    else Some(upickle.default.read[SignedChainAnchor](Files.readString(anchorFile)))
  }
