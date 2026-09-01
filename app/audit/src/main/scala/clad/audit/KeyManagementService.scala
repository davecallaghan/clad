package clad.audit

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

case class Signature(
  value: Array[Byte],
  keyId: String,
  algorithm: String
)

sealed trait KmsError
case class SigningFailed(reason: String) extends KmsError
case class VerificationFailed(reason: String) extends KmsError
case class KeyUnavailable(keyId: String) extends KmsError

trait KeyManagementService:
  def keyId: String
  def sign(data: Array[Byte]): Either[KmsError, Signature]
  def verify(data: Array[Byte], signature: Signature): Either[KmsError, Boolean]

class HmacKeyManagement(secretKey: Array[Byte], val keyId: String) extends KeyManagementService:
  private val algorithmName = "HmacSHA256"

  def sign(data: Array[Byte]): Either[KmsError, Signature] =
    try
      val mac = Mac.getInstance(algorithmName)
      mac.init(SecretKeySpec(secretKey, algorithmName))
      val hmac = mac.doFinal(data)
      Right(Signature(hmac, keyId, algorithmName))
    catch
      case e: Exception => Left(SigningFailed(e.getMessage))

  def verify(data: Array[Byte], signature: Signature): Either[KmsError, Boolean] =
    try
      val mac = Mac.getInstance(algorithmName)
      mac.init(SecretKeySpec(secretKey, algorithmName))
      val expected = mac.doFinal(data)
      Right(MessageDigest.isEqual(expected, signature.value))
    catch
      case e: Exception => Left(VerificationFailed(e.getMessage))
