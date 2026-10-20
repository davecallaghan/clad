package clad.mdr

import clad.gateway.config.Fixtures
import clad.gateway.`export`.BundleExport
import clad.gateway.keys.Keys
import clad.gateway.store.{Jdbc, TestDatabase}
import clad.monitoring.{AlertCategory, AlertSeverity}
import com.sun.net.httpserver.HttpServer
import org.scalatest.funsuite.AnyFunSuite
import java.net.InetSocketAddress
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}
import java.util.concurrent.atomic.AtomicInteger
import scala.jdk.CollectionConverters.*

class AlertWriterSpec extends AnyFunSuite:
  private val cfg = TestSnapshots.cfg
  private val now = Instant.now()

  /** A local webhook answering `status` (changeable); it keeps every body it received. */
  private final class Hook(@volatile var status: Int):
    val bodies = scala.collection.mutable.ArrayBuffer.empty[ujson.Value]
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/", ex => {
      val body = String(ex.getRequestBody.readAllBytes(), "UTF-8")
      bodies.synchronized(bodies += ujson.read(body))
      ex.sendResponseHeaders(status, -1)
      ex.close()
    })
    server.start()
    val webhook = Webhook(s"http://127.0.0.1:${server.getAddress.getPort}/alerts", Duration.ofSeconds(cfg.webhookTimeoutSeconds))
    def stop(): Unit = server.stop(0)

  private def keysDir(): Path =
    val keys = Files.createTempDirectory("keys")
    Seq("components", "supervisor").foreach(r => Keys.generate(keys, r))
    keys

  private final class Rig(hook: Option[Hook]):
    val db = TestDatabase.fresh()
    val lockers: Path = Files.createTempDirectory("lockers")
    private val keys = keysDir()
    val incidents = Incidents(db.readerUrl, (from, until, out) => BundleExport.write(out, from, until, None, db.readerUrl,
      None, Map.empty, Files.createTempDirectory("anchors"), keys, Fixtures.approvedConfig().governance, "personal_finance")
      .map(_ => ()), lockers)
    val writer = AlertWriter(db.readerUrl, cfg, hook.map(_.webhook), incidents)

    def count(sql: String): Int =
      Jdbc.withConnection(db.readerUrl) { c =>
        val rs = c.createStatement().executeQuery(sql)
        rs.next()
        rs.getInt(1)
      }.fold(fail(_), identity)

  private def alert(key: String, sev: AlertSeverity, ids: Vector[String] = Vector.empty) =
    MdrAlert(key, sev, AlertCategory.Governance, "block_rate", ids, "s", Some("agent"))

  test("a P3 alert: a row due raised + 240 min, a webhook POST with dueAt and recommendation, then a delivery row") {
    val hook = Hook(200)
    try
      val rig = Rig(Some(hook))
      val id = rig.writer.write(alert("block_rate:p:1", AlertSeverity.P3Medium), now, now).fold(fail(_), identity)
      val due = Jdbc.withConnection(rig.db.readerUrl) { c =>
        val ps = c.prepareStatement("SELECT due_at, raised_at, severity FROM mdr.alert WHERE alert_id = ?::uuid")
        ps.setString(1, id)
        val rs = ps.executeQuery()
        rs.next()
        (Duration.between(rs.getTimestamp(2).toInstant, rs.getTimestamp(1).toInstant), rs.getString(3))
      }.fold(fail(_), identity)
      assert(due == (Duration.ofMinutes(cfg.dueMinutes(AlertSeverity.P3Medium)), "P3"))
      assert(cfg.dueMinutes(AlertSeverity.P3Medium) == 240)
      val body = hook.bodies.synchronized(hook.bodies.toVector)
      assert(body.map(_("alertId").str) == Vector(id))
      assert(body.forall(b => b.obj.contains("dueAt") && b.obj.contains("recommendation") && b("severity").str == "P3"))
      assert(rig.count(s"SELECT count(*) FROM mdr.alert_delivery WHERE alert_id = '$id'") == 1)
      assert(rig.count("SELECT count(*) FROM mdr.incident") == 0, "only P1 and P2 open incidents")
    finally hook.stop()
  }

  test("a webhook answering 500 leaves the alert undelivered; redeliver later delivers it and writes the row") {
    val hook = Hook(500)
    try
      val rig = Rig(Some(hook))
      val id = rig.writer.write(alert("block_rate:p:2", AlertSeverity.P3Medium), now, now).fold(fail(_), identity)
      assert(rig.count("SELECT count(*) FROM mdr.alert") == 1, "the row is written before the POST")
      assert(rig.count("SELECT count(*) FROM mdr.alert_delivery") == 0)
      assert(rig.writer.redeliver(Vector(id), now) == Right(0), "still failing: nothing delivered")
      hook.status = 200
      assert(rig.writer.redeliver(Vector(id), now) == Right(1))
      assert(rig.count(s"SELECT count(*) FROM mdr.alert_delivery WHERE alert_id = '$id'") == 1)
      assert(hook.bodies.synchronized(hook.bodies.toVector).map(_("alertId").str) == Vector(id, id, id))
    finally hook.stop()
  }

  test("a P1 alert opens an incident whose locker conformance reads and whose files are not writable") {
    val rig = Rig(None)
    val id = rig.writer.write(alert("ghost:x", AlertSeverity.P1Critical), now.minusSeconds(60), now).fold(fail(_), identity)
    val (locker, error) = Jdbc.withConnection(rig.db.readerUrl) { c =>
      val ps = c.prepareStatement("SELECT locker_path, export_error FROM mdr.incident WHERE alert_id = ?::uuid")
      ps.setString(1, id)
      val rs = ps.executeQuery()
      assert(rs.next(), "an incident row")
      (Path.of(rs.getString(1)), Option(rs.getString(2)))
    }.fold(fail(_), identity)
    assert(error.isEmpty)
    assert(locker.startsWith(rig.lockers))
    assert(clad.conformance.BundleReader.read(locker).isRight)
    val files = Files.walk(locker).iterator().asScala.toVector
    assert(files.size > 1 && files.forall(p => !Files.isWritable(p)), files.filter(Files.isWritable(_)))
    assert(rig.count(s"SELECT count(*) FROM mdr.alert_delivery WHERE alert_id = '$id' AND target = 'log'") == 1,
      "with no webhook the alert is logged and recorded as delivered to log")
  }

  test("a failed locker export records export_error and writes a P2 locker_export_failed alert; the alert is stored") {
    val db = TestDatabase.fresh()
    val incidents = Incidents(db.readerUrl, (_, _, _) => Left("disk full"), Files.createTempDirectory("lockers"))
    val writer = AlertWriter(db.readerUrl, cfg, None, incidents)
    val r = writer.write(alert("ghost:y", AlertSeverity.P1Critical), now, now)
    assert(r.isRight, r)
    val rows = Jdbc.withConnection(db.readerUrl) { c =>
      val rs = c.createStatement().executeQuery("SELECT a.severity, a.detector, i.export_error FROM mdr.alert a " +
        "LEFT JOIN mdr.incident i ON i.alert_id = a.alert_id ORDER BY a.raised_at, a.detector")
      Iterator.continually(rs).takeWhile(_.next()).map(r => (r.getString(1), r.getString(2), Option(r.getString(3)))).toVector
    }.fold(fail(_), identity)
    assert(rows.toSet == Set(("P1", "block_rate", Some("disk full")), ("P2", "locker_export_failed", None)), rows)
  }

  test("a P1 alert whose incident row was not written gets its incident on the repeat, and only one") {
    val db = TestDatabase.fresh()
    val lockers = Files.createTempDirectory("lockers")
    val ok = (_: Instant, _: Instant, out: Path) => Right(Files.createDirectories(out)).map(_ => ())
    val broken = AlertWriter(db.readerUrl, cfg, None, Incidents("jdbc:postgresql://127.0.0.1:1/nowhere?user=x&password=y", ok, lockers))
    assert(broken.write(alert("ghost:z", AlertSeverity.P1Critical), now, now).isLeft)
    val writer = AlertWriter(db.readerUrl, cfg, None, Incidents(db.readerUrl, ok, lockers))
    val id = writer.write(alert("ghost:z", AlertSeverity.P1Critical), now, now).fold(fail(_), identity)
    writer.write(alert("ghost:z", AlertSeverity.P1Critical), now, now).fold(fail(_), identity)
    val incidents = Jdbc.withConnection(db.readerUrl) { c =>
      val rs = c.createStatement().executeQuery("SELECT alert_id FROM mdr.incident")
      Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toVector
    }.fold(fail(_), identity)
    assert(incidents == Vector(id))
  }

  test("the same dedupe key twice is one row, and the second write returns the first alert's id") {
    val rig = Rig(None)
    val first = rig.writer.write(alert("block_rate:p:3", AlertSeverity.P4Low), now, now)
    val second = rig.writer.write(alert("block_rate:p:3", AlertSeverity.P4Low), now, now.plusSeconds(60))
    assert(first.isRight && first == second)
    assert(rig.count("SELECT count(*) FROM mdr.alert") == 1)
    assert(rig.count("SELECT count(*) FROM mdr.alert_delivery") == 1, "a repeat is not delivered again")
  }
