package clad.gateway.`export`

import clad.audit.ChainAnchorStore
import clad.gateway.config.Governance
import clad.gateway.keys.Keys
import clad.gateway.store.Jdbc
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.sql.{Connection, ResultSet}
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.util.control.NonFatal

/** Writes an evidence bundle (conformance spec section 4) from the governance and mdr schemas, the chain anchor,
  * the public keys and the egress log. A section whose source is absent is left out: absent evidence is not
  * the same as zero items. */
object BundleExport:
  val ExporterName = "clad-gateway export 0.1.0"
  private val KeyRoles = Vector("components", "supervisor")

  private def jsonl(path: Path, rows: Iterable[String]): Unit =
    Files.writeString(path, rows.map(_ + "\n").mkString, UTF_8)

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

  /** Writes gil, audit, degraded, configs, artifacts and the MDR files. Does nothing before `migrate` has run. */
  private def tables(out: Path, c: Connection, since: Instant, until: Instant): Unit =
    val present = query(c, "SELECT to_regclass('governance.audit_record') IS NOT NULL")(_.getBoolean(1)).contains(true)
    if present then
      jsonl(out.resolve("gil.jsonl"), query(c,
        "SELECT interaction_id, registered_at, project, principal, tier, metadata FROM governance.gil ORDER BY registered_at") { rs =>
        val meta = ujson.read(rs.getString("metadata")).obj
        val columns = Seq("project", "principal", "tier").map(k => k -> ujson.Str(rs.getString(k)))
        ujson.write(ujson.Obj("interactionId" -> rs.getString("interaction_id"), "registeredAt" -> at(rs, "registered_at").toString,
          "metadata" -> ujson.Obj.from(meta.toSeq.filterNot(kv => columns.exists(_._1 == kv._1)) ++ columns)))
      })
      jsonl(out.resolve("audit.jsonl"), query(c, "SELECT line FROM governance.audit_record ORDER BY seq")(_.getString(1)))
      jsonl(out.resolve("degraded.jsonl"), query(c, "SELECT line FROM governance.degraded_record ORDER BY seq")(_.getString(1)))
      val configs = Files.createDirectories(out.resolve("configs"))
      query(c, "SELECT digest, body FROM governance.config_version")(rs => rs.getString(1) -> rs.getBytes(2))
        .foreach((digest, body) => Files.write(configs.resolve(s"$digest.json"), body))
      val artifacts = Files.createDirectories(out.resolve("artifacts"))
      query(c, "SELECT digest, body FROM governance.artifact")(rs => rs.getString(1) -> rs.getString(2))
        .foreach((digest, body) => Files.writeString(artifacts.resolve(s"$digest.json"), body, UTF_8))
      jsonl(out.resolve("mdr_alerts.jsonl"), query(c,
        "SELECT alert_id, raised_at, severity, taxonomy, detector, interaction_ids, summary FROM mdr.alert " +
          "WHERE raised_at BETWEEN ? AND ? ORDER BY raised_at", Seq(utc(since), utc(until))) { rs =>
        val ids = rs.getArray("interaction_ids").getArray.asInstanceOf[Array[AnyRef]].toVector.map(i => ujson.Str(i.toString))
        ujson.write(ujson.Obj("alertId" -> rs.getString("alert_id"), "raisedAt" -> at(rs, "raised_at").toString,
          "severity" -> rs.getString("severity"), "taxonomy" -> rs.getString("taxonomy"), "detector" -> rs.getString("detector"),
          "interactionIds" -> ujson.Arr.from(ids), "summary" -> rs.getString("summary")))
      })
      jsonl(out.resolve("mdr_heartbeats.jsonl"), query(c,
        "SELECT ts, detector FROM mdr.heartbeat WHERE ts BETWEEN ? AND ? ORDER BY ts", Seq(utc(since), utc(until))) { rs =>
        ujson.write(ujson.Obj("ts" -> at(rs, "ts").toString, "detector" -> rs.getString("detector")))
      })

  private def window(start: Instant, end: Instant): ujson.Obj = ujson.Obj("start" -> start.toString, "end" -> end.toString)

  private def keyFiles(out: Path, keysDir: Path): Either[String, Map[String, String]] =
    val keysOut = Files.createDirectories(out.resolve("keys"))
    KeyRoles.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) { (acc, role) =>
      for
        ids <- acc
        pem <- Keys.publicPem(keysDir, role)
        id <- Keys.keyIdOf(pem)
      yield
        Files.writeString(keysOut.resolve(s"$id.pub.pem"), pem, UTF_8)
        ids + (role -> id)
    }

  /** A configured egress log that does not exist is left out of the bundle, loudly. */
  private def present(p: Path): Boolean =
    val exists = Files.exists(p)
    if !exists then System.err.println(s"CLAD_GATEWAY_WARN egress log $p does not exist; the bundle has no infra.jsonl")
    exists

  def write(out: Path, since: Instant, until: Instant, probe: Option[(Instant, Instant)], readerUrl: String,
            egressLog: Option[Path], listeners: Map[String, String], anchorDir: Path, keysDir: Path,
            governance: Governance, app: String): Either[String, Path] =
    try
      if nonEmptyDir(out) then Left(s"$out is not empty: a reused directory could keep stale files, so export to a new one")
      else
        Files.createDirectories(out)
        // Order matters: the anchor is read first, then every table in one REPEATABLE READ snapshot. An anchor
        // older than the chain is always consistent for R-ANCHOR; the reverse order could see an anchor ahead of it.
        for
          keyIds <- keyFiles(out, keysDir)
          anchor <- ChainAnchorStore(anchorDir).read.toEither.left.map(e => s"cannot read the chain anchor in $anchorDir: ${e.getMessage}")
          _ <- Jdbc.withSnapshot(readerUrl)(c => tables(out, c, since, until))
          infra <- egressLog.filter(present).fold[Either[String, Option[Vector[ujson.Obj]]]](Right(None))(
            p => CaddyLog.read(p, listeners).map(Some(_)))
        yield
          anchor.foreach(a => jsonl(out.resolve("anchors.jsonl"), Seq(ujson.write(ujson.Obj("ts" -> a.anchor.updatedAt.toString,
            "chainLength" -> a.anchor.count, "headDigest" -> a.anchor.headDigest)))))
          infra.foreach(rows => jsonl(out.resolve("infra.jsonl"), rows.map(ujson.write(_))))
          val meta = ujson.Obj("bundleVersion" -> 1, "app" -> app, "window" -> window(since, until))
          probe.foreach((s, e) => meta("probeWindow") = window(s, e))
          meta("projects") = governance.bundleProjects
          meta("keys") = ujson.Obj.from(keyIds.toSeq.map((r, id) => r -> ujson.Str(id)))
          meta("exportedAt") = Instant.now().toString
          meta("exporter") = ExporterName
          Files.writeString(out.resolve("bundle.json"), ujson.write(meta, indent = 2), UTF_8)
          out
    catch case NonFatal(e) => Left(s"bundle export failed: ${e.getClass.getSimpleName}: ${e.getMessage}")

  private def nonEmptyDir(p: Path): Boolean =
    Files.isDirectory(p) && {
      val s = Files.list(p)
      try s.findAny().isPresent finally s.close()
    }
