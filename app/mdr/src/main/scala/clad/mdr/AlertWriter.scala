package clad.mdr

import clad.gateway.store.Jdbc
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.sql.{Connection, Timestamp}
import java.time.{Duration, Instant, OffsetDateTime}
import java.util.UUID

/** Writes alerts. The row is written before any delivery, so a failed webhook loses nothing: the alert stays
  * undelivered (no mdr.alert_delivery row) and `redeliver` retries it next cycle. P1 and P2 open an incident. */
final class AlertWriter(dbUrl: String, cfg: MdrConfig, webhook: Option[Webhook], incidents: Incidents):
  private val LogTarget = "log"
  private val IncidentSeverities = Set(AlertSeverity.P1Critical, AlertSeverity.P2High)

  private def recommendation(severity: String, detector: String): String = (severity, detector) match
    case ("P1", "ghosts") => "Contain the project (POST /admin/contain) until the bypass is explained."
    case ("P1", _)        => "Respond now: preserve the incident locker and the store."
    case ("P2", _)        => "Investigate within the due time; the incident locker holds the evidence."
    case _                => "Review by the due time."

  private def json(id: String, raised: Instant, severity: String, taxonomy: String, detector: String, ids: Vector[String],
                   summary: String, due: Instant): ujson.Obj =
    ujson.Obj("alertId" -> id, "raisedAt" -> raised.toString, "severity" -> severity, "taxonomy" -> taxonomy,
      "detector" -> detector, "interactionIds" -> ujson.Arr.from(ids.map(ujson.Str(_))), "summary" -> summary,
      "dueAt" -> due.toString, "recommendation" -> recommendation(severity, detector))

  /** Inserts the alert; a dedupe-key conflict means another cycle wrote it: Right(existing id, false). */
  private def insert(a: MdrAlert, now: Instant): Either[String, (String, Boolean, ujson.Obj)] =
    val id = UUID.randomUUID().toString
    val due = now.plus(Duration.ofMinutes(cfg.dueMinutes(a.severity)))
    Jdbc.withConnection(dbUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO mdr.alert (alert_id, raised_at, severity, taxonomy, detector, interaction_ids, " +
        "summary, due_at, dedupe_key) VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (dedupe_key) DO NOTHING")
      ps.setString(1, id)
      ps.setTimestamp(2, Timestamp.from(now))
      ps.setString(3, a.severityLabel)
      ps.setString(4, a.taxonomy)
      ps.setString(5, a.detector)
      ps.setArray(6, c.createArrayOf("text", a.interactionIds.toArray[AnyRef]))
      ps.setString(7, a.summary)
      ps.setTimestamp(8, Timestamp.from(due))
      ps.setString(9, a.dedupeKey)
      if ps.executeUpdate() == 1 then
        (id, true, json(id, now, a.severityLabel, a.taxonomy, a.detector, a.interactionIds, a.summary, due))
      else
        val q = c.prepareStatement("SELECT alert_id FROM mdr.alert WHERE dedupe_key = ?")
        q.setString(1, a.dedupeKey)
        val rs = q.executeQuery()
        rs.next()
        (rs.getString(1), false, ujson.Obj())
    }

  private def delivered(alertId: String, target: String, now: Instant): Either[String, Unit] =
    Jdbc.withConnection(dbUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO mdr.alert_delivery (alert_id, delivered_at, target) VALUES (?::uuid, ?, ?) " +
        "ON CONFLICT (alert_id) DO NOTHING")
      ps.setString(1, alertId)
      ps.setTimestamp(2, Timestamp.from(now))
      ps.setString(3, target)
      ps.executeUpdate()
      ()
    }

  /** Webhook POST, or with no webhook one JSON line on stdout; either way a delivery row on success. A failure is
    * logged with the alert id and left for redeliver. */
  private def deliver(alertId: String, body: ujson.Obj, now: Instant): Boolean =
    val sent = webhook match
      case Some(h) => h.post(body).map(_ => h.target)
      case None =>
        println(s"CLAD_MDR_ALERT ${ujson.write(body)}")
        Right(LogTarget)
    sent.flatMap(delivered(alertId, _, now)) match
      case Right(_) => true
      case Left(e) =>
        System.err.println(s"CLAD_MDR_DELIVERY_FAILED alert=$alertId $e")
        false

  /** The earliest moment of the alert's interactions (GIL registration, or an infra@<ts> entry), else `fallback`. */
  private def earliest(a: MdrAlert, fallback: Instant): Either[String, Instant] =
    val infra = a.interactionIds.filter(_.startsWith("infra@")).flatMap(i => scala.util.Try(Instant.parse(i.stripPrefix("infra@"))).toOption)
    val ids = a.interactionIds.filterNot(_.startsWith("infra@"))
    Jdbc.withConnection(dbUrl) { c =>
      val ps = c.prepareStatement("SELECT min(registered_at) AS t FROM governance.gil WHERE interaction_id::text = ANY (?)")
      ps.setArray(1, c.createArrayOf("text", ids.toArray[AnyRef]))
      val rs = ps.executeQuery()
      rs.next()
      Option(rs.getObject("t", classOf[OffsetDateTime])).map(_.toInstant)
    }.map(gil => (gil.toVector ++ infra :+ fallback).min)

  /** Returns the alert id. `windowStart` bounds the incident export when the alert names no interaction. */
  def write(a: MdrAlert, windowStart: Instant, now: Instant): Either[String, String] =
    insert(a, now).flatMap { (id, isNew, body) =>
      if !isNew then Right(id)
      else
        deliver(id, body, now)
        if !IncidentSeverities.contains(a.severity) then Right(id)
        else
          for
            from <- earliest(a, windowStart)
            opened <- incidents.open(id, from, now)
            _ <- opened._2.fold[Either[String, Unit]](Right(()))(err =>
              // no incident for this alert, so a failing export cannot open incidents without end
              insert(MdrAlert(s"locker_export_failed:${opened._1}", AlertSeverity.P2High, AlertCategory.Governance,
                "locker_export_failed", a.interactionIds, s"The evidence locker for incident ${opened._1} (alert $id) could " +
                  s"not be exported: $err", a.project), now).map((fid, fresh, fbody) => if fresh then deliver(fid, fbody, now) else ())
                .flatMap(_ => Left(s"incident ${opened._1}: locker export failed: $err")))
          yield id
    }

  /** Retries delivery of alerts that have no delivery row; returns how many were delivered now. */
  def redeliver(alertIds: Vector[String], now: Instant): Either[String, Int] =
    Jdbc.withConnection(dbUrl) { c =>
      alertIds.flatMap { id =>
        val ps = c.prepareStatement("SELECT raised_at, severity, taxonomy, detector, interaction_ids, summary, due_at " +
          "FROM mdr.alert WHERE alert_id = ?::uuid")
        ps.setString(1, id)
        val rs = ps.executeQuery()
        Option.when(rs.next()) {
          json(id, rs.getObject(1, classOf[OffsetDateTime]).toInstant, rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getArray(5).getArray.asInstanceOf[Array[AnyRef]].toVector.map(_.toString), rs.getString(6),
            rs.getObject(7, classOf[OffsetDateTime]).toInstant)
        }.map(id -> _)
      }
    }.map(_.count((id, body) => deliver(id, body, now)))
