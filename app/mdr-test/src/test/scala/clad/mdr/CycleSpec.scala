package clad.mdr

import clad.audit.SignedAuditRecord
import clad.gateway.store.{Jdbc, TestDatabase}
import clad.monitoring.{AlertCategory, AlertSeverity}
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import java.time.{Duration, Instant}
import scala.collection.mutable.ArrayBuffer

class CycleSpec extends AnyFunSuite:
  import TestSnapshots.{cfg, empty}

  private val t0 = Instant.parse("2026-10-19T12:00:00Z")

  private final class FakeSource(snap: Either[String, Snapshot], beat: Option[Instant] = None, first: Option[Instant] = None)
      extends Source:
    val windows = ArrayBuffer.empty[Window]
    def snapshot(w: Window, lookback: Duration): Either[String, Snapshot] =
      windows += w
      snap.map(_.copy(window = w))
    def lastHeartbeat: Either[String, Option[Instant]] = Right(beat)
    def earliest: Either[String, Option[Instant]] = Right(first)
    def chain: Either[String, Vector[SignedAuditRecord]] = Right(Vector.empty)

  private final class Fixed(val id: String, alerts: Vector[MdrAlert]) extends Detector:
    def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] = Right(alerts)

  private final class Throws(val id: String) extends Detector:
    def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] = throw IllegalStateException("boom")

  private def alert(key: String) = MdrAlert(key, AlertSeverity.P3Medium, AlertCategory.Compliance, "flags", Vector("i1"), "s", None)

  private final class Rig(source: Source, detectors: Vector[Detector], dbUrl: Option[String] = None):
    val db = TestDatabase.fresh()
    val url: String = dbUrl.getOrElse(db.readerUrl)
    val beats = ArrayBuffer.empty[(String, Instant)]
    private var clockAt = t0
    private val noExport = (_: Instant, _: Instant, out: java.nio.file.Path) => Right(Files.createDirectories(out)).map(_ => ())
    val writer = AlertWriter(url, cfg, None, Incidents(url, noExport, Files.createTempDirectory("lockers")))
    val cycle = Cycle(source, detectors, writer, (d, at) => { beats += (d -> at); Right(()) }, cfg, () => clockAt)
    def tick(seconds: Long): Unit = clockAt = clockAt.plusSeconds(seconds)
    def alerts: Vector[(String, String)] =
      Jdbc.withConnection(db.readerUrl) { c =>
        val rs = c.createStatement().executeQuery("SELECT detector, dedupe_key FROM mdr.alert ORDER BY dedupe_key")
        Iterator.continually(rs).takeWhile(_.next()).map(r => (r.getString(1), r.getString(2))).toVector
      }.fold(fail(_), identity)

  test("one heartbeat per detector per cycle, at the cycle time") {
    val rig = Rig(FakeSource(Right(empty)), Vector(Fixed("a", Vector.empty), Fixed("b", Vector.empty)))
    rig.cycle.once().fold(fail(_), identity)
    rig.tick(cfg.intervalSeconds)
    rig.cycle.once().fold(fail(_), identity)
    assert(rig.beats.toVector == Vector("a" -> t0, "b" -> t0, "a" -> t0.plusSeconds(cfg.intervalSeconds),
      "b" -> t0.plusSeconds(cfg.intervalSeconds)))
  }

  test("a throwing detector still gets its heartbeat and a P2 detector_failed alert; the others run") {
    val rig = Rig(FakeSource(Right(empty)), Vector(Throws("bad"), Fixed("good", Vector(alert("flag:i1")))))
    val r = rig.cycle.once().fold(fail(_), identity)
    assert(r.detectorFailures == Vector("bad") && r.alertsWritten == 2)
    assert(rig.beats.map(_._1).toSet == Set("bad", "good"))
    assert(rig.alerts == Vector(("bad", s"detector_failed:bad:$t0"), ("flags", "flag:i1")))
  }

  test("two cycles over the same snapshot write no duplicate alerts") {
    val rig = Rig(FakeSource(Right(empty)), Vector(Fixed("flags", Vector(alert("flag:i1"), alert("flag:i2")))))
    rig.cycle.once().fold(fail(_), identity)
    rig.tick(cfg.intervalSeconds)
    rig.cycle.once().fold(fail(_), identity)
    assert(rig.alerts.map(_._2) == Vector("flag:i1", "flag:i2"))
  }

  test("a snapshot Left writes no heartbeat and once returns Left") {
    val rig = Rig(FakeSource(Left("db down")), Vector(Fixed("a", Vector.empty)))
    assert(rig.cycle.once() == Left("db down"))
    assert(rig.beats.isEmpty)
  }

  test("an alert that cannot be stored writes no heartbeat, so the next cycle examines the same rows") {
    val bad = "jdbc:postgresql://127.0.0.1:1/nowhere?user=x&password=y"
    val rig = Rig(FakeSource(Right(empty)), Vector(Fixed("flags", Vector(alert("flag:i1")))), Some(bad))
    assert(rig.cycle.once().left.exists(_.contains("flag:i1")))
    assert(rig.beats.isEmpty)
  }

  test("the window starts at the earliest row on the first run, then at the last heartbeat minus the grace") {
    val first = FakeSource(Right(empty), first = Some(t0.minus(Duration.ofDays(30))))
    Rig(first, Vector.empty).cycle.once().fold(fail(_), identity)
    assert(first.windows.toVector == Vector(Window(t0.minus(Duration.ofDays(30)), t0)))
    val later = FakeSource(Right(empty), beat = Some(t0.minusSeconds(cfg.intervalSeconds)))
    Rig(later, Vector.empty).cycle.once().fold(fail(_), identity)
    assert(later.windows.toVector == Vector(Window(t0.minusSeconds(cfg.intervalSeconds + cfg.inFlightGraceSeconds), t0)))
    val fresh = FakeSource(Right(empty))
    Rig(fresh, Vector.empty).cycle.once().fold(fail(_), identity)
    assert(fresh.windows.toVector == Vector(Window(t0, t0)), "an empty store: a window of no time, nothing missed")
  }
