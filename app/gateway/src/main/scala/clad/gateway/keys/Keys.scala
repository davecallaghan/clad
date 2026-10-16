package clad.gateway.keys

import clad.audit.Ed25519KeyManagement
import java.nio.file.{Files, Path}
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

  /** Temp file (mode 600) in the same directory, then a hard link to the final name: the link fails
    * atomically if the target exists, so a key that appeared meanwhile is never replaced, and a failed
    * write never leaves a partial `<role>.ed25519`. The temp name is removed on every path. */
  private def linkPrivateKey(dir: Path, priv: Path, text: String): Unit =
    val tmp = Files.createTempFile(dir, priv.getFileName.toString, ".tmp",
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
    try
      Files.writeString(tmp, text)
      Files.createLink(priv, tmp)
      Files.delete(tmp)
    catch case e: Throwable =>
      try Files.deleteIfExists(tmp)
      catch case c: java.io.IOException => e.addSuppressed(c)
      throw e

  /** Writes a new key pair; Left if `<role>.ed25519` already exists (never replaces it). The private key
    * is linked into place first and the public key written only by the caller that won that link. */
  private[keys] def writeNew(dir: Path, role: String): Either[String, Boolean] =
    val priv = dir.resolve(s"$role.ed25519")
    try
      Files.createDirectories(dir)
      val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
      linkPrivateKey(dir, priv, pem("PRIVATE KEY", kp.getPrivate.getEncoded))
      Files.writeString(dir.resolve(s"$role.ed25519.pub.pem"), pem("PUBLIC KEY", kp.getPublic.getEncoded))
      Right(true)
    catch
      case _: java.nio.file.FileAlreadyExistsException => Left(s"$priv already exists")
      case e: java.io.IOException => Left(s"cannot write keys to $dir: ${e.getMessage}")
      case e: UnsupportedOperationException => Left(s"cannot set mode 600 or link keys in $dir: ${e.getMessage}")
      case e: java.security.GeneralSecurityException => Left(s"cannot generate Ed25519 key: ${e.getMessage}")

  def generate(dir: Path, role: String): Either[String, Boolean] =
    if Files.exists(dir.resolve(s"$role.ed25519")) then Right(false) else writeNew(dir, role)
