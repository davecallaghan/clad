package clad.gateway.store

import org.scalatest.funsuite.AnyFunSuite

class MigrateSpec extends AnyFunSuite:
  test("every table gets the refuse-change triggers; the writer gets INSERT and SELECT only") {
    val text = Migrate.statements("w", "r").fold(fail(_), identity).mkString("\n")
    Migrate.Tables.foreach { t =>
      assert(text.contains(s"BEFORE UPDATE OR DELETE ON $t"), t)
      assert(text.contains(s"BEFORE TRUNCATE ON $t"), t)
    }
    assert(text.contains("GRANT INSERT, SELECT ON ALL TABLES IN SCHEMA governance TO clad_gov_writer"))
  }

  test("a password that is not alphanumeric is refused (it is inlined into ALTER ROLE)") {
    assert(Migrate.statements("x'; DROP", "r").isLeft)
  }

  test("migrate is idempotent; the writer cannot change rows and the triggers stop even the superuser") {
    val db = TestDatabase.fresh()
    assert(Migrate.run(db.adminUrl, TestDatabase.WriterPw, TestDatabase.ReaderPw) == Right(()))
    Jdbc.withConnection(db.writerUrl)(_.createStatement().execute(
      "INSERT INTO governance.gil VALUES (gen_random_uuid(), now(), 'agent', 'test', 'Critical', '{}')")).fold(fail(_), identity)
    for stmt <- Seq("UPDATE governance.gil SET tier = 'Low'", "DELETE FROM governance.gil", "TRUNCATE governance.gil") do
      assert(Jdbc.withConnection(db.writerUrl)(_.createStatement().execute(stmt)).isLeft, stmt)
      assert(Jdbc.withConnection(db.adminUrl)(_.createStatement().execute(stmt)).left.exists(_.contains("append-only")), stmt)
  }

  test("redaction removes the password from ALTER ROLE text") {
    val out = Migrate.redact("ALTER ROLE clad_gov_writer PASSWORD 'sekretpw1'")
    assert(!out.contains("sekretpw1"))
    assert(out.contains("'***'"))
  }

  test("a failing migration reports the statement index and never the password") {
    val db = TestDatabase.fresh()
    val r = Migrate.run(db.readerUrl, "sekretwriter1", "sekretreader1")
    assert(r.isLeft)
    val msg = r.left.getOrElse("")
    assert(msg.startsWith("statement "))
    assert(!msg.contains("sekretwriter1") && !msg.contains("sekretreader1"))
  }
