package clad.gateway.store

import clad.audit.{KeyManagementService, SignedAuditRecord}
import clad.integrity.{GilEntry, SignedDegradedRecord}
import clad.runtime.AuditRecord
import scala.collection.mutable
import scala.util.control.NonFatal

/** In-memory GatewayStore for tests. */
final class MemoryStore extends GatewayStore:
  val gil = mutable.ArrayBuffer.empty[GilEntry]
  val lines = mutable.ArrayBuffer.empty[SignedAuditRecord]
  val degradedLines = mutable.ArrayBuffer.empty[SignedDegradedRecord]
  val configs = mutable.Map.empty[String, String]
  val artifacts = mutable.Map.empty[String, String]
  var failRegister: Boolean = false

  def register(entry: GilEntry): Either[String, Unit] = synchronized {
    if failRegister then Left("db down")
    else GatewayStore.requiredKeys.find(k => !entry.metadata.contains(k)) match
      case Some(k) => Left(s"GIL metadata needs $k")
      case None => gil += entry; Right(())
  }

  def appendLinked(build: Option[String] => AuditRecord, kms: KeyManagementService): Either[String, SignedAuditRecord] =
    synchronized {
      for
        record <- try Right(build(lines.lastOption.map(_.record.digest)))
                  catch case NonFatal(e) => Left(s"building the audit record failed: ${e.getMessage}")
        _ <- record.interactionId.toRight("audit record has no interaction id")
        signed <- try SignedAuditRecord.sign(record, kms).left.map(e => s"signing failed: $e")
                  catch case NonFatal(e) => Left(s"signing failed: ${e.getMessage}")
      yield
        lines += signed
        signed
    }

  def degraded(r: SignedDegradedRecord): Either[String, Unit] = synchronized { degradedLines += r; Right(()) }

  def putConfig(digest: String, project: String, json: String): Either[String, Unit] = synchronized {
    configs.getOrElseUpdate(digest, json); Right(())
  }

  def putArtifact(digest: String, json: String): Either[String, Unit] = synchronized {
    artifacts.getOrElseUpdate(digest, json); Right(())
  }
