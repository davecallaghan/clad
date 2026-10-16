package clad.gateway.store

/** Governance schema: GIL, audit chain, degraded records, configs, artifacts, input signals, MDR tables.
  * Run as the Postgres superuser. Tables are owned by clad_gov_owner (NOLOGIN); the gateway writes as
  * clad_gov_writer (INSERT, SELECT); MDR and the exporter read as clad_gov_reader. Triggers refuse UPDATE, DELETE
  * and TRUNCATE on every table, so even the owner role cannot rewrite history without dropping the trigger first. */
object Migrate:
  val Tables: Vector[String] = Vector("governance.gil", "governance.audit_record", "governance.degraded_record",
    "governance.config_version", "governance.artifact", "governance.input_signal", "mdr.alert", "mdr.incident")

  private val Ddl: Vector[String] = Vector(
    "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clad_gov_owner') THEN CREATE ROLE clad_gov_owner NOLOGIN; END IF; END $$",
    "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clad_gov_writer') THEN CREATE ROLE clad_gov_writer LOGIN; END IF; END $$",
    "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'clad_gov_reader') THEN CREATE ROLE clad_gov_reader LOGIN; END IF; END $$",
    "CREATE SCHEMA IF NOT EXISTS governance AUTHORIZATION clad_gov_owner",
    "CREATE SCHEMA IF NOT EXISTS mdr AUTHORIZATION clad_gov_owner",
    """CREATE TABLE IF NOT EXISTS governance.gil (interaction_id uuid PRIMARY KEY, registered_at timestamptz NOT NULL,
      |  project text NOT NULL, principal text NOT NULL, tier text NOT NULL, metadata jsonb NOT NULL)""".stripMargin,
    """CREATE TABLE IF NOT EXISTS governance.audit_record (seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      |  interaction_id uuid NOT NULL, component text NOT NULL, digest text NOT NULL UNIQUE,
      |  previous_digest text UNIQUE, line text NOT NULL, written_at timestamptz NOT NULL DEFAULT now())""".stripMargin,
    """CREATE TABLE IF NOT EXISTS governance.degraded_record (seq bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      |  interaction_id uuid NOT NULL, line text NOT NULL, written_at timestamptz NOT NULL DEFAULT now())""".stripMargin,
    """CREATE TABLE IF NOT EXISTS governance.config_version (digest text PRIMARY KEY, project text NOT NULL,
      |  body bytea NOT NULL, created_at timestamptz NOT NULL DEFAULT now())""".stripMargin,
    "CREATE TABLE IF NOT EXISTS governance.artifact (digest text PRIMARY KEY, body text NOT NULL)",
    """CREATE TABLE IF NOT EXISTS governance.input_signal (interaction_id uuid NOT NULL, detector text NOT NULL,
      |  detector_version text NOT NULL, category text NOT NULL, score double precision NOT NULL,
      |  observed_at timestamptz NOT NULL DEFAULT now())""".stripMargin,
    """CREATE TABLE IF NOT EXISTS mdr.alert (alert_id uuid PRIMARY KEY, raised_at timestamptz NOT NULL,
      |  severity text NOT NULL, taxonomy text NOT NULL, detector text NOT NULL, interaction_ids text[] NOT NULL,
      |  summary text NOT NULL, due_at timestamptz NOT NULL)""".stripMargin,
    "CREATE TABLE IF NOT EXISTS mdr.heartbeat (ts timestamptz NOT NULL, detector text NOT NULL)",
    """CREATE TABLE IF NOT EXISTS mdr.incident (incident_id uuid PRIMARY KEY, alert_id uuid NOT NULL,
      |  opened_at timestamptz NOT NULL, locker_path text NOT NULL)""".stripMargin,
    """CREATE OR REPLACE FUNCTION governance.refuse_change() RETURNS trigger LANGUAGE plpgsql AS $$
      |  BEGIN RAISE EXCEPTION 'governance records are append-only (% on %)', TG_OP, TG_TABLE_NAME; END $$""".stripMargin)

  private def ownershipAndGrants: Vector[String] =
    val owners = (Tables :+ "mdr.heartbeat").map(t => s"ALTER TABLE $t OWNER TO clad_gov_owner")
    val grants = Vector(
      "GRANT USAGE ON SCHEMA governance TO clad_gov_writer, clad_gov_reader",
      "GRANT USAGE ON SCHEMA mdr TO clad_gov_reader",
      "GRANT INSERT, SELECT ON ALL TABLES IN SCHEMA governance TO clad_gov_writer",
      "GRANT SELECT ON ALL TABLES IN SCHEMA governance TO clad_gov_reader",
      "GRANT INSERT, SELECT ON mdr.alert, mdr.heartbeat, mdr.incident TO clad_gov_reader")
    val triggers = Tables.flatMap { t =>
      val name = t.split("\\.")(1)
      Vector(
        s"DROP TRIGGER IF EXISTS ${name}_no_change ON $t",
        s"CREATE TRIGGER ${name}_no_change BEFORE UPDATE OR DELETE ON $t FOR EACH ROW EXECUTE FUNCTION governance.refuse_change()",
        s"DROP TRIGGER IF EXISTS ${name}_no_truncate ON $t",
        s"CREATE TRIGGER ${name}_no_truncate BEFORE TRUNCATE ON $t FOR EACH STATEMENT EXECUTE FUNCTION governance.refuse_change()")
    }
    owners ++ grants ++ triggers

  private val Alphanumeric = "[A-Za-z0-9]+"

  def statements(writerPw: String, readerPw: String): Either[String, Vector[String]] =
    if !writerPw.matches(Alphanumeric) || !readerPw.matches(Alphanumeric) then Left("role passwords must be alphanumeric")
    else Right(Ddl ++ ownershipAndGrants ++ Vector(
      s"ALTER ROLE clad_gov_writer PASSWORD '$writerPw'",
      s"ALTER ROLE clad_gov_reader PASSWORD '$readerPw'"))

  def run(adminUrl: String, writerPw: String, readerPw: String): Either[String, Unit] =
    statements(writerPw, readerPw).flatMap { stmts =>
      Jdbc.withConnection(adminUrl) { c =>
        stmts.foldLeft[Either[String, Unit]](Right(())) { (acc, s) =>
          acc.flatMap { _ =>
            try { c.createStatement().execute(s); Right(()) }
            catch case e: java.sql.SQLException => Left(s"${e.getSQLState}: ${e.getMessage} [$s]")
          }
        }
      }.flatten
    }
