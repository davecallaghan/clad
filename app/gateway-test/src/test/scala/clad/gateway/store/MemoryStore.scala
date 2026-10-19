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
  var explodeRegister: Option[RuntimeException] = None
  var failPutConfig: Option[String] = None

  def register(entry: GilEntry): Either[String, Unit] = synchronized {
    explodeRegister.foreach(e => throw e)
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
    failPutConfig.toLeft(()).map { _ => configs.getOrElseUpdate(digest, json); () }
  }

  val signals = mutable.ArrayBuffer.empty[(String, clad.gateway.pipeline.InputSignal, java.time.Instant)]
  var failSignals: Boolean = false
  def inputSignal(interactionId: String, s: clad.gateway.pipeline.InputSignal, at: java.time.Instant): Either[String, Unit] =
    synchronized { if failSignals then Left("signal store down") else { signals += ((interactionId, s, at)); Right(()) } }

  def putArtifact(digest: String, json: String): Either[String, Unit] = synchronized {
    artifacts.getOrElseUpdate(digest, json); Right(())
  }
