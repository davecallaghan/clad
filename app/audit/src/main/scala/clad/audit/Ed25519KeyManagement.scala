package clad.audit

import java.security.{KeyFactory, KeyPairGenerator, PrivateKey, PublicKey}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64

/** Ed25519 signing for audit records. A verifier built from a public key alone can check
  * every signature and can sign nothing, which is what independent verification (AI4)
  * needs: the party checking the chain never holds the key that wrote it. */
final class Ed25519KeyManagement(
  privateKey: Option[PrivateKey],
  val publicKey: PublicKey,
  val keyId: String
) extends KeyManagementService:
  private val algorithmName = "Ed25519"

  def sign(data: Array[Byte]): Either[KmsError, Signature] =
    privateKey match
      case None => Left(KeyUnavailable(keyId))
      case Some(pk) =>
        try
          val s = java.security.Signature.getInstance(algorithmName)
          s.initSign(pk)
          s.update(data)
          Right(Signature(s.sign(), keyId, algorithmName))
        catch case e: java.security.GeneralSecurityException => Left(SigningFailed(e.getMessage))

  def verify(data: Array[Byte], signature: Signature): Either[KmsError, Boolean] =
    try
      val s = java.security.Signature.getInstance(algorithmName)
      s.initVerify(publicKey)
      s.update(data)
      Right(s.verify(signature.value))
    catch case e: java.security.GeneralSecurityException => Left(VerificationFailed(e.getMessage))

object Ed25519KeyManagement:
  private def pemBody(pem: String): Either[KmsError, Array[Byte]] =
    val body = pem.linesIterator.filterNot(_.startsWith("-----")).mkString.trim
    try Right(Base64.getDecoder.decode(body))
    catch case e: IllegalArgumentException => Left(KeyUnavailable(s"malformed PEM: ${e.getMessage}"))

  private def toPem(kind: String, der: Array[Byte]): String =
    val b64 = Base64.getMimeEncoder(64, "\n".getBytes("UTF-8")).encodeToString(der)
    s"-----BEGIN $kind-----\n$b64\n-----END $kind-----\n"

  def fromPublicPem(pem: String, keyId: String): Either[KmsError, Ed25519KeyManagement] =
    pemBody(pem).flatMap { der =>
      try Right(Ed25519KeyManagement(None, KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(der)), keyId))
      catch case e: java.security.GeneralSecurityException => Left(KeyUnavailable(s"$keyId: ${e.getMessage}"))
    }

  def fromPrivatePem(privatePem: String, publicPem: String, keyId: String): Either[KmsError, Ed25519KeyManagement] =
    for
      pub <- fromPublicPem(publicPem, keyId)
      der <- pemBody(privatePem)
      priv <- (try Right(KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(der)))
               catch case e: java.security.GeneralSecurityException => Left(KeyUnavailable(s"$keyId: ${e.getMessage}")))
    yield Ed25519KeyManagement(Some(priv), pub.publicKey, keyId)

  /** Fresh key pair, for tests and fixtures. Returns the signer and the public key as PEM. */
  def generate(keyId: String): (Ed25519KeyManagement, String) =
    val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    (Ed25519KeyManagement(Some(kp.getPrivate), kp.getPublic, keyId), toPem("PUBLIC KEY", kp.getPublic.getEncoded))
