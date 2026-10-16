package clad.gateway.store

import java.util.concurrent.atomic.AtomicInteger

final case class TestDb(adminUrl: String, writerUrl: String, readerUrl: String)

/** A fresh, migrated database per call on the server named by CLAD_TEST_PG_ADMIN_URL (a superuser JDBC URL).
  * Names start clad_gw_test_ so a live store can never be the target.
  * Roles are cluster-wide, so running the tests resets the clad_gov_writer/clad_gov_reader passwords on the target
  * server: CLAD_TEST_PG_ADMIN_URL must point at a server with no live clad store. */
object TestDatabase:
  val WriterPw = "testwriterpw"
  val ReaderPw = "testreaderpw"
  private val n = AtomicInteger()

  lazy val admin: String = sys.env.getOrElse("CLAD_TEST_PG_ADMIN_URL",
    throw AssertionError("CLAD_TEST_PG_ADMIN_URL is not set: point it at a Postgres superuser, e.g. jdbc:postgresql://localhost:5432/postgres?user=david"))

  private def withDb(url: String, db: String): String =
    val (base, query) = url.split("\\?", 2) match { case Array(b, q) => (b, s"?$q"); case Array(b) => (b, "") }
    s"${base.substring(0, base.lastIndexOf('/'))}/$db$query"

  private def withUser(url: String, user: String, pw: String): String =
    val base = url.split("\\?", 2)(0)
    s"$base?user=$user&password=$pw"

  def fresh(): TestDb =
    val db = s"clad_gw_test_${ProcessHandle.current().pid()}_${n.incrementAndGet()}"
    Jdbc.withConnection(admin) { c =>
      c.createStatement().execute(s"DROP DATABASE IF EXISTS $db WITH (FORCE)")
      c.createStatement().execute(s"CREATE DATABASE $db")
    }.fold(e => throw AssertionError(e), identity)
    val dbAdmin = withDb(admin, db)
    Migrate.run(dbAdmin, WriterPw, ReaderPw).fold(e => throw AssertionError(e), identity)
    TestDb(dbAdmin, withUser(dbAdmin, "clad_gov_writer", WriterPw), withUser(dbAdmin, "clad_gov_reader", ReaderPw))
