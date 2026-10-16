package clad.gateway.store

import clad.audit.*
import clad.integrity.{DegradedRecordCodec, GilEntry, SignedDegradedRecord}
import clad.runtime.AuditRecord
import java.sql.{Connection, SQLException, Timestamp}
import java.time.Instant
import scala.util.control.NonFatal

final class PgStore(writerUrl: String, lockKey: Long, anchors: ChainAnchorStore, anchorKms: KeyManagementService)
    extends GatewayStore:

  private val SigningState = "XX000"

  def register(entry: GilEntry): Either[String, Unit] =
    GatewayStore.requiredKeys.find(k => !entry.metadata.contains(k)) match
      case Some(k) => Left(s"GIL metadata needs $k")
      case None =>
        val meta = ujson.write(ujson.Obj.from(entry.metadata.toSeq.map((k, v) => k -> ujson.Str(v))))
        Jdbc.withConnection(writerUrl) { c =>
          val ps = c.prepareStatement("INSERT INTO governance.gil VALUES (?::uuid, ?, ?, ?, ?, ?::jsonb)")
          ps.setString(1, entry.interactionId.value)
          ps.setTimestamp(2, Timestamp.from(entry.registeredAt))
          ps.setString(3, entry.metadata("project"))
          ps.setString(4, entry.metadata("principal"))
          ps.setString(5, entry.metadata("tier"))
          ps.setString(6, meta)
          ps.executeUpdate()
          ()
        }

  def appendLinked(build: Option[String] => AuditRecord, kms: KeyManagementService): Either[String, SignedAuditRecord] =
    Jdbc.withTransaction(writerUrl)(c => appendIn(c, build, kms)).map { (signed, count) =>
      anchor(count + 1, signed.record.digest)
      signed
    }

  /** Runs inside the transaction. Any failure is thrown as an SQLException so the transaction rolls back. */
  private def appendIn(c: Connection, build: Option[String] => AuditRecord, kms: KeyManagementService)
      : (SignedAuditRecord, Int) =
    val lock = c.prepareStatement("SELECT pg_advisory_xact_lock(?)")
    lock.setLong(1, lockKey)
    lock.execute()
    val rs = c.createStatement().executeQuery(
      "SELECT digest, (SELECT count(*) FROM governance.audit_record) FROM governance.audit_record " +
        "WHERE seq = (SELECT max(seq) FROM governance.audit_record)")
    val (head, count) = if rs.next() then (Some(rs.getString(1)), rs.getInt(2)) else (None, 0)
    val record =
      try build(head)
      catch case NonFatal(e) => throw SQLException(s"building the audit record failed: ${e.getMessage}", SigningState, e)
    val signed = SignedAuditRecord.sign(record, kms) match
      case Right(s) => s
      case Left(e)  => throw SQLException(s"signing failed: $e", SigningState)
    val interactionId = record.interactionId.map(_.value).getOrElse(
      throw SQLException("audit record has no interaction id", SigningState))
    val ps = c.prepareStatement(
      "INSERT INTO governance.audit_record (interaction_id, component, digest, previous_digest, line) " +
        "VALUES (?::uuid, ?, ?, ?, ?)")
    ps.setString(1, interactionId)
    ps.setString(2, record.component.orNull)
    ps.setString(3, record.digest)
    ps.setString(4, record.previousDigest.orNull)
    ps.setString(5, AuditRecordCodec.encode(signed))
    ps.executeUpdate()
    (signed, count)

  /** The record is already committed; a lost anchor is reported on stderr and does not fail the append. */
  private def anchor(count: Int, digest: String): Unit =
    val result = ChainAnchor.sign(ChainAnchor(count, digest, Instant.now()), anchorKms) match
      case Left(e)  => Left(s"anchor signing failed: $e")
      case Right(s) => anchors.write(s).toEither.left.map(_.getMessage)
    result.left.foreach(reason => System.err.println(s"GOVERNANCE_ANCHOR_FAILED $reason"))

  def degraded(r: SignedDegradedRecord): Either[String, Unit] =
    Jdbc.withConnection(writerUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO governance.degraded_record (interaction_id, line) VALUES (?::uuid, ?)")
      ps.setString(1, r.record.interactionId.value)
      ps.setString(2, DegradedRecordCodec.encode(r))
      ps.executeUpdate()
      ()
    }

  def putConfig(digest: String, project: String, json: String): Either[String, Unit] =
    Jdbc.withConnection(writerUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO governance.config_version (digest, project, body) VALUES (?, ?, ?) ON CONFLICT (digest) DO NOTHING")
      ps.setString(1, digest)
      ps.setString(2, project)
      ps.setBytes(3, json.getBytes("UTF-8"))
      ps.executeUpdate()
      ()
    }

  def putArtifact(digest: String, json: String): Either[String, Unit] =
    Jdbc.withConnection(writerUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO governance.artifact (digest, body) VALUES (?, ?) ON CONFLICT (digest) DO NOTHING")
      ps.setString(1, digest)
      ps.setString(2, json)
      ps.executeUpdate()
      ()
    }
