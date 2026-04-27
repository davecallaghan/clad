package clad.audit

import java.time.Instant
import scala.util.Try

trait Lift[F[_]]:
  def fromTry[A](t: => Try[A]): F[A]

object Lift:
  given Lift[Try] with
    def fromTry[A](t: => Try[A]): Try[A] = t

sealed trait StoreError extends Throwable
case class WriteError(reason: String) extends StoreError:
  override def getMessage: String = s"WriteError: $reason"
case class ReadError(reason: String) extends StoreError:
  override def getMessage: String = s"ReadError: $reason"
case class CorruptionDetected(reason: String) extends StoreError:
  override def getMessage: String = s"CorruptionDetected: $reason"

trait AuditStore[F[_]]:
  def append(record: SignedAuditRecord): F[Unit]
  def readAll: F[Vector[SignedAuditRecord]]
  def readSince(timestamp: Instant): F[Vector[SignedAuditRecord]]
  def count: F[Int]
