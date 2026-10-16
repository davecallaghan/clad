package clad.gateway.keys

import clad.audit.Ed25519KeyManagement
import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.file.attribute.PosixFilePermissions
import java.security.{KeyFactory, KeyPairGenerator, MessageDigest}
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object Keys:
  /** Key ids are the first 16 hex characters of sha256(public key DER) (spec section 5). */
  private val KeyIdHexLength = 16

  private def pem(kind: String, der: Array[Byte]): String =
    s"-----BEGIN $kind-----\n${Base64.getMimeEncoder(64, "\n".getBytes("UTF-8")).encodeToString(der)}\n-----END $kind-----\n"

  private def der(pemText: String): Either[String, Array[Byte]] =
    try Right(Base64.getDecoder.decode(pemText.linesIterator.filterNot(_.startsWith("-----")).mkString.trim))
    catch case e: IllegalArgumentException => Left(s"malformed PEM: ${e.getMessage}")

  private def readText(p: Path, what: String): Either[String, String] =
    if !Files.exists(p) then Left(s"$p is missing (run `clad-gateway keys`)")
    else
      try Right(Files.readString(p))
      catch case e: java.io.IOException => Left(s"cannot read $what $p: ${e.getMessage}")

  def keyIdOf(publicPem: String): Either[String, String] = der(publicPem).flatMap { d =>
    try
      val spki = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(d)).getEncoded
      Right(MessageDigest.getInstance("SHA-256").digest(spki).map("%02x".format(_)).mkString.substring(0, KeyIdHexLength))
    catch case e: java.security.GeneralSecurityException => Left(s"not an Ed25519 public key: ${e.getMessage}")
  }

  def publicPem(dir: Path, role: String): Either[String, String] =
    readText(dir.resolve(s"$role.ed25519.pub.pem"), "public key")

  def load(dir: Path, role: String): Either[String, Ed25519KeyManagement] =
    for
      pub <- publicPem(dir, role)
      privText <- readText(dir.resolve(s"$role.ed25519"), "private key")
      kid <- keyIdOf(pub)
      kms <- Ed25519KeyManagement.fromPrivatePem(privText, pub, kid).left.map(_.toString)
    yield kms

  def generate(dir: Path, role: String): Either[String, Boolean] =
    val priv = dir.resolve(s"$role.ed25519")
    if Files.exists(priv) then Right(false)
    else
      try
        Files.createDirectories(dir)
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        // Public half first: the private file is the "already generated" marker, so it lands last.
        Files.writeString(dir.resolve(s"$role.ed25519.pub.pem"), pem("PUBLIC KEY", kp.getPublic.getEncoded))
        Files.createFile(priv, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
        Files.writeString(priv, pem("PRIVATE KEY", kp.getPrivate.getEncoded), StandardOpenOption.TRUNCATE_EXISTING)
        Right(true)
      catch
        case e: java.io.IOException => Left(s"cannot write keys to $dir: ${e.getMessage}")
        case e: UnsupportedOperationException => Left(s"cannot set mode 600 on keys in $dir: ${e.getMessage}")
        case e: java.security.GeneralSecurityException => Left(s"cannot generate Ed25519 key: ${e.getMessage}")
