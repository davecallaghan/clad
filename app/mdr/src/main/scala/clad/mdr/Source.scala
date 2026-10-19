package clad.mdr

import clad.audit.{AuditRecordCodec, SignedAuditRecord}
import clad.gateway.`export`.CaddyLog
import clad.gateway.pipeline.InputSignal
import clad.gateway.store.Jdbc
import clad.integrity.{DegradedRecordCodec, SignedDegradedRecord}
import java.nio.file.{Files, Path}
import java.sql.{Connection, ResultSet}
import java.time.{Duration, Instant, OffsetDateTime, ZoneOffset}

final case class Window(start: Instant, end: Instant):
  def contains(t: Instant): Boolean = !t.isBefore(start) && !t.isAfter(end)

// MDR's own type: main code must not depend on the conformance verifier, which stays independent of what it verifies
final case class InfraCall(ts: Instant, host: String, status: Int, interactionId: Option[String])
final case class GilRow(interactionId: String, registeredAt: Instant, project: String, principal: String,
                        tier: String, metadata: ujson.Obj)
final case class RecordRow(interactionId: String, component: String, outcome: Option[String], configDigest: String,
                           entries: Vector[clad.runtime.AuditEntry], timestamp: Instant)

/** What one cycle sees. The rows cover the window widened by the lookback (the block-rate baseline); `gilIds` holds
  * every registered interaction id, so a call is a ghost only when no GIL entry exists for it at all.
  * `alertedKeys` are the dedupe keys already written, plus `ghost:<id>` for every id a ghosts alert listed. */
final case class Snapshot(window: Window, gil: Vector[GilRow], records: Vector[RecordRow],
                          degraded: Vector[SignedDegradedRecord], signals: Vector[(String, InputSignal, Instant)],
                          infra: Vector[InfraCall], configDigests: Set[String], gilIds: Set[String],
                          alertedKeys: Set[String], undelivered: Vector[String])

trait Source:
  def snapshot(w: Window, lookback: Duration): Either[String, Snapshot]
  def lastHeartbeat: Either[String, Option[Instant]]
  /** The earliest GIL registration or infra call: where the first window starts. */
  def earliest: Either[String, Option[Instant]]
  def chain: Either[String, Vector[SignedAuditRecord]]

object Source:
  val GhostKeyPrefix = "ghost:"

/** Reads as clad_gov_reader, in one REPEATABLE READ snapshot. The infra log is read through the gateway's CaddyLog
  * reader, the one the bundle export uses, so MDR and the bundle see the same infra entries. */
final class PgSource(dbUrl: String, egressLog: Path, listeners: Map[String, String]) extends Source:
  private def query[A](c: Connection, sql: String, params: Seq[Object] = Seq.empty)(f: ResultSet => A): Vector[A] =
    val st = c.prepareStatement(sql)
    try
      params.zipWithIndex.foreach((p, i) => st.setObject(i + 1, p))
      val rs = st.executeQuery()
      try Iterator.continually(rs).takeWhile(_.next()).map(f).toVector
      finally rs.close()
    finally st.close()

  private def at(rs: ResultSet, col: String): Instant = rs.getObject(col, classOf[OffsetDateTime]).toInstant
  private def utc(t: Instant): Object = OffsetDateTime.ofInstant(t, ZoneOffset.UTC)

  private def all[A](xs: Vector[Either[String, A]]): Either[String, Vector[A]] =
    xs.foldLeft[Either[String, Vector[A]]](Right(Vector.empty))((acc, x) => acc.flatMap(v => x.map(v :+ _)))

  /** An absent egress log is no infra evidence, not an error: the gateway's export makes the same call. */
  private def infra: Either[String, Vector[InfraCall]] =
    if !Files.exists(egressLog) then Right(Vector.empty)
    else CaddyLog.read(egressLog, listeners).map(_.map { o =>
      InfraCall(Instant.parse(o("ts").str), o("host").str, o("status").num.toInt, o("interactionId").strOpt)
    })

  private def row(r: SignedAuditRecord): Either[String, RecordRow] =
    for
      iid <- r.record.interactionId.map(_.value).toRight(s"audit record ${r.record.digest} has no interaction id")
      comp <- r.record.component.toRight(s"audit record ${r.record.digest} has no component")
    yield RecordRow(iid, comp, r.record.outcome, r.record.configDigest, r.record.entries, r.record.timestamp)

  def snapshot(w: Window, lookback: Duration): Either[String, Snapshot] =
    val from = utc(w.start.minus(lookback))
    val to = utc(w.end)
    for
      calls <- infra
      raw <- Jdbc.withSnapshot(dbUrl) { c =>
        val gil = query(c, "SELECT interaction_id, registered_at, project, principal, tier, metadata FROM governance.gil " +
          "WHERE registered_at BETWEEN ? AND ? ORDER BY registered_at", Seq(from, to)) { rs =>
          GilRow(rs.getString(1), at(rs, "registered_at"), rs.getString(3), rs.getString(4), rs.getString(5),
            ujson.read(rs.getString(6)).obj)
        }
        val gilIds = query(c, "SELECT interaction_id FROM governance.gil")(_.getString(1)).toSet
        val lines = query(c, "SELECT line FROM governance.audit_record WHERE written_at BETWEEN ? AND ? ORDER BY seq",
          Seq(from, to))(_.getString(1))
        val degraded = query(c, "SELECT line FROM governance.degraded_record WHERE written_at BETWEEN ? AND ? ORDER BY seq",
          Seq(from, to))(_.getString(1))
        val signals = query(c, "SELECT interaction_id, detector, detector_version, category, score, observed_at " +
          "FROM governance.input_signal WHERE observed_at BETWEEN ? AND ? ORDER BY observed_at", Seq(from, to)) { rs =>
          (rs.getString(1), InputSignal(rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5)), at(rs, "observed_at"))
        }
        val digests = query(c, "SELECT digest FROM governance.config_version")(_.getString(1)).toSet
        val keys = query(c, "SELECT dedupe_key, detector, interaction_ids FROM mdr.alert") { rs =>
          val ids = rs.getArray(3).getArray.asInstanceOf[Array[AnyRef]].toVector.map(_.toString)
          Option(rs.getString(1)).toVector ++ (if rs.getString(2) == "ghosts" then ids.map(Source.GhostKeyPrefix + _) else Vector.empty)
        }.flatten.toSet
        val undelivered = query(c, "SELECT a.alert_id FROM mdr.alert a WHERE NOT EXISTS " +
          "(SELECT 1 FROM mdr.alert_delivery d WHERE d.alert_id = a.alert_id) ORDER BY a.raised_at")(_.getString(1))
        (gil, gilIds, lines, degraded, signals, digests, keys, undelivered)
      }
      (gil, gilIds, lines, degradedLines, signals, digests, keys, undelivered) = raw
      records <- all(lines.map(l => AuditRecordCodec.decode(l).flatMap(row)))
      degraded <- all(degradedLines.map(DegradedRecordCodec.decode))
    yield
      val span = Window(w.start.minus(lookback), w.end)
      Snapshot(w, gil, records, degraded, signals, calls.filter(e => span.contains(e.ts)), digests, gilIds, keys, undelivered)

  def lastHeartbeat: Either[String, Option[Instant]] =
    Jdbc.withConnection(dbUrl)(c => query(c, "SELECT max(ts) AS ts FROM mdr.heartbeat")(rs =>
      Option(rs.getObject("ts", classOf[OffsetDateTime])).map(_.toInstant)).flatten.headOption)

  def earliest: Either[String, Option[Instant]] =
    for
      calls <- infra
      gil <- Jdbc.withConnection(dbUrl)(c => query(c, "SELECT min(registered_at) AS ts FROM governance.gil")(rs =>
        Option(rs.getObject("ts", classOf[OffsetDateTime])).map(_.toInstant)).flatten)
    yield (calls.map(_.ts) ++ gil).minOption

  def chain: Either[String, Vector[SignedAuditRecord]] =
    Jdbc.withConnection(dbUrl)(c => query(c, "SELECT line FROM governance.audit_record ORDER BY seq")(_.getString(1)))
      .flatMap(lines => all(lines.map(AuditRecordCodec.decode)))
