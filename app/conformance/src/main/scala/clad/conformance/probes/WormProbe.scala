package clad.conformance.probes

import clad.conformance.*
import java.nio.file.{Files, Path}
import java.sql.{Connection, DriverManager, SQLException}

object WormProbe:
  private val Title = "WORM storage"

  /** Tries UPDATE, DELETE and TRUNCATE on one existing row of each table, each in a savepoint,
    * and rolls everything back. Never commits. */
  def run(c: WormConfig): RequirementResult =
    try probe(c, Files.readString(Path.of(c.passwordFile)).trim)
    catch case e: java.io.IOException =>
      RequirementResult("R-AI1", Title, "audit", Verdict.NotRun,
        reason = s"cannot read the password file ${c.passwordFile}: ${e.getClass.getSimpleName}")

  private def probe(c: WormConfig, password: String): RequirementResult =
    var conn: Connection = null
    try
      conn = DriverManager.getConnection(c.jdbcUrl, c.user, password)
      conn.setAutoCommit(false)
      val findings = c.tables.flatMap(t => probeTable(conn, t))
      val accepted = findings.filter(_.startsWith("ACCEPTED"))
      val empty = findings.filter(_.startsWith("EMPTY"))
      if accepted.nonEmpty then RequirementResult("R-AI1", Title, "audit", Verdict.Violated, findings, s"${accepted.size} statements were accepted")
      else if empty.nonEmpty then RequirementResult("R-AI1", Title, "audit", Verdict.NotRun, findings, s"${empty.size} tables were empty")
      else RequirementResult("R-AI1", Title, "audit", Verdict.Satisfied, findings)
    catch case e: SQLException => RequirementResult("R-AI1", Title, "audit", Verdict.NotRun, reason = s"cannot connect: ${e.getSQLState} ${e.getMessage}")
    finally if conn != null then { conn.rollback(); conn.close() }

  private def probeTable(conn: Connection, table: String): Vector[String] =
    val Array(schema, name) = table.split("\\.", 2)
    val colStmt = conn.prepareStatement("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position")
    colStmt.setString(1, schema); colStmt.setString(2, name)
    val rs = colStmt.executeQuery()
    if !rs.next() then return Vector(s"EMPTY $table: no columns visible")
    val col = rs.getString(1)
    val count = conn.createStatement().executeQuery(s"SELECT count(*) FROM $table")
    count.next()
    if count.getLong(1) == 0 then return Vector(s"EMPTY $table: no rows to probe")
    val row = s"ctid = (SELECT min(ctid) FROM $table)"
    Vector(s"UPDATE $table SET $col = $col WHERE $row", s"DELETE FROM $table WHERE $row", s"TRUNCATE $table").map { sql =>
      val sp = conn.setSavepoint()
      try
        val n = conn.createStatement().executeUpdate(sql)
        conn.rollback(sp)
        s"ACCEPTED $sql ($n rows)"
      catch case e: SQLException =>
        conn.rollback(sp)
        s"refused ${sql.takeWhile(_ != ' ')} on $table: ${e.getSQLState}"
    }
