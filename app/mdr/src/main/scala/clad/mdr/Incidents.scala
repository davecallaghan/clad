package clad.mdr

import clad.gateway.store.Jdbc
import java.nio.file.{Files, Path}
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*

/** Opens an incident for a P1 or P2 alert: exports the alert window as an evidence bundle into
  * `<lockerDir>/<incident id>/` and makes every file and directory in it read-only, then inserts mdr.incident (the
  * table is append-only, so the row is written once, with the export error when the export failed). */
final class Incidents(dbUrl: String, exportWindow: (Instant, Instant, Path) => Either[String, Unit], lockerDir: Path):

  private def readOnly(dir: Path): Either[String, Unit] =
    try
      val s = Files.walk(dir)
      try
        // deepest first, so a directory is locked after its files
        val paths = s.iterator().asScala.toVector.reverse
        paths.find(p => !p.toFile.setWritable(false, false)).fold[Either[String, Unit]](Right(()))(p =>
          Left(s"cannot make $p read-only"))
      finally s.close()
    catch case e: java.io.IOException => Left(s"cannot lock $dir: ${e.getMessage}")

  /** Right(incident id, export error if any); Left only when the incident row itself could not be written. */
  def open(alertId: String, from: Instant, now: Instant): Either[String, (String, Option[String])] =
    val id = UUID.randomUUID().toString
    val locker = lockerDir.resolve(id)
    val exported = exportWindow(from, now, locker).flatMap(_ => readOnly(locker))
    val error = exported.left.toOption
    Jdbc.withConnection(dbUrl) { c =>
      val ps = c.prepareStatement("INSERT INTO mdr.incident (incident_id, alert_id, opened_at, locker_path, export_error) " +
        "VALUES (?::uuid, ?::uuid, ?, ?, ?)")
      ps.setString(1, id)
      ps.setString(2, alertId)
      ps.setTimestamp(3, Timestamp.from(now))
      ps.setString(4, locker.toString)
      ps.setString(5, error.orNull)
      ps.executeUpdate()
      (id, error)
    }
