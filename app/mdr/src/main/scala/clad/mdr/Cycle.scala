package clad.mdr

import clad.gateway.store.Jdbc
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.sql.Timestamp
import java.time.{Duration, Instant}
import scala.util.control.NonFatal

final case class CycleReport(window: Window, alertsWritten: Int, detectorFailures: Vector[String], redelivered: Int)

/** One monitoring cycle: window, snapshot, every detector, alerts, heartbeats, redelivery.
  *
  * The window starts at the last heartbeat minus `in_flight_grace_seconds` (interactions in flight at the last cycle
  * are examined again), or on the first run at the earliest infra call or GIL entry, so calls made before MDR existed
  * are alerted. Dedupe keys make re-examining a row harmless.
  *
  * Alerts are stored before heartbeats. A failing detector still gets its heartbeat and a `detector_failed` alert.
  * When an alert cannot be stored the cycle writes no heartbeat and returns Left: the cursor stays where it was, the
  * next cycle detects the same rows again, and R-MDR-LIVE shows the gap instead of silence that reads as health. A
  * snapshot failure likewise writes no heartbeat. */
final class Cycle(source: Source, detectors: Vector[Detector], writer: AlertWriter,
                  heartbeat: (String, Instant) => Either[String, Unit], cfg: MdrConfig, clock: () => Instant):

  private val lookback = Duration.ofHours(cfg.blockRateBaselineHours)

  private def window(now: Instant): Either[String, Window] =
    source.lastHeartbeat.flatMap {
      case Some(beat) => Right(Window(beat.minusSeconds(cfg.inFlightGraceSeconds), now))
      case None       => source.earliest.map(first => Window(first.filter(_.isBefore(now)).getOrElse(now), now))
    }

  /** A detector that throws is a failed detector, reported with its exception, never a crashed cycle. */
  private def run(d: Detector, s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    try d.run(s, now)
    catch case NonFatal(e) => Left(s"${e.getClass.getSimpleName}: ${e.getMessage}")

  private def failed(id: String, error: String, now: Instant): MdrAlert =
    MdrAlert(s"detector_failed:$id:$now", AlertSeverity.P2High, AlertCategory.Governance, id, Vector.empty,
      s"Detector $id failed; its rows in this window were not examined by it: $error", None)

  def once(): Either[String, CycleReport] =
    val now = clock()
    for
      w <- window(now)
      s <- source.snapshot(w, lookback)
      results = detectors.map(d => d.id -> run(d, s, now))
      failures = results.collect { case (id, Left(e)) => id -> e }
      alerts = results.flatMap(_._2.toOption.toVector.flatten) ++ failures.map(failed(_, _, now))
      written = alerts.map(a => writer.write(a, w.start, now).left.map(e => s"${a.dedupeKey}: $e"))
      _ <- written.collect { case Left(e) => e } match
        case Vector() => Right(())
        case errors   => Left(s"${errors.size} alerts not stored, so no heartbeat was written: ${errors.mkString("; ")}")
      _ <- detectors.foldLeft[Either[String, Unit]](Right(()))((acc, d) => acc.flatMap(_ => heartbeat(d.id, now)))
      redelivered <- writer.redeliver(s.undelivered, now)
    yield
      failures.foreach((id, e) => System.err.println(s"CLAD_MDR_DETECTOR_FAILED detector=$id $e"))
      CycleReport(w, alerts.size, failures.map(_._1), redelivered)

object Cycle:
  /** Writes one heartbeat row (as clad_gov_reader, which has INSERT on mdr.*). */
  def pgHeartbeat(dbUrl: String)(detector: String, at: Instant): Either[String, Unit] =
    Jdbc.withConnection(dbUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO mdr.heartbeat (ts, detector) VALUES (?, ?)")
      ps.setTimestamp(1, Timestamp.from(at))
      ps.setString(2, detector)
      ps.executeUpdate()
      ()
    }
