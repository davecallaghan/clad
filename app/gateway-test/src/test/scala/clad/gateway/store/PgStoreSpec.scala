package clad.gateway.store

import clad.audit.*
import clad.core.*
import clad.evaluation.MechanicalDetail
import clad.integrity.GilEntry
import clad.runtime.*
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

class PgStoreSpec extends AnyFunSuite:
  val (kms, pub) = Ed25519KeyManagement.generate("k1")
  val Threads = 20

  def record(prev: Option[String]): AuditRecord =
    val t = Instant.parse("2026-10-16T12:00:00Z")
    AuditRecord("sha256:a", Vector(AuditEntry(Constraint.Obligation(PropertyId.unsafe("p"), Level.Enterprise), "1",
      EvaluabilityClass.Mechanical, true, MechanicalDetail(true), t)), "sha256:c", t, prev,
      Some(InteractionId(UUID.randomUUID().toString)), Some("epg"), Some(Surface.Prompt), Some("pass"))

  def store(db: TestDb) = PgStore(db.writerUrl, 7243001L, ChainAnchorStore(Files.createTempDirectory("anchors")), kms)

  def lines(db: TestDb): Vector[SignedAuditRecord] =
    Jdbc.withConnection(db.readerUrl) { c =>
      val rs = c.createStatement().executeQuery("SELECT line FROM governance.audit_record ORDER BY seq")
      Iterator.continually(rs).takeWhile(_.next()).map(r => AuditRecordCodec.decode(r.getString(1)).fold(fail(_), identity)).toVector
    }.fold(fail(_), identity)

  test("concurrent appends form one chain") {
    val db = TestDatabase.fresh()
    val s = store(db)
    given ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(Threads))
    Await.result(Future.sequence((1 to Threads).map(_ => Future(s.appendLinked(record, kms)))), Duration.Inf)
      .foreach(r => assert(r.isRight, r))
    val chain = lines(db)
    assert(chain.size == Threads && chain.headOption.exists(_.record.previousDigest.isEmpty))
    chain.zip(chain.drop(1)).foreach((a, b) => assert(b.record.previousDigest.contains(a.record.digest)))
    assert(chain.forall(_.isAuthentic(kms) == Right(true)))
  }

  test("a second store instance (a restart) continues the stored chain") {
    val db = TestDatabase.fresh()
    store(db).appendLinked(record, kms).fold(fail(_), identity)
    val second = store(db).appendLinked(record, kms).fold(fail(_), identity)
    assert(second.record.previousDigest.contains(lines(db).headOption.map(_.record.digest).get))
  }

  test("the anchor names the chain length and head") {
    val db = TestDatabase.fresh()
    val dir = Files.createTempDirectory("anchors")
    val s = PgStore(db.writerUrl, 7243001L, ChainAnchorStore(dir), kms)
    val last = (1 to 3).map(_ => s.appendLinked(record, kms).fold(fail(_), identity)).last
    val anchor = ChainAnchorStore(dir).read.get.get
    assert(anchor.anchor.count == 3 && anchor.anchor.headDigest == last.record.digest)
  }

  test("concurrent appends leave a parseable anchor naming the full chain") {
    val db = TestDatabase.fresh()
    val dir = Files.createTempDirectory("anchors")
    val s = PgStore(db.writerUrl, 7243001L, ChainAnchorStore(dir), kms)
    given ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(Threads))
    Await.result(Future.sequence((1 to Threads).map(_ => Future(s.appendLinked(record, kms)))), Duration.Inf)
      .foreach(r => assert(r.isRight, r))
    val anchor = ChainAnchorStore(dir).read.get.get
    assert(anchor.anchor.count == Threads && lines(db).lastOption.exists(_.record.digest == anchor.anchor.headDigest))
  }

  test("register, configs and artifacts are stored; configs and artifacts are idempotent") {
    val db = TestDatabase.fresh()
    val s = store(db)
    val iid = InteractionId(UUID.randomUUID().toString)
    assert(s.register(GilEntry(iid, Instant.now(), Map("project" -> "agent", "principal" -> "api", "tier" -> "Critical",
      "template_id" -> "agent.answer"))) == Right(()))
    assert(s.putConfig("sha256:c", "agent", "{}") == Right(()) && s.putConfig("sha256:c", "agent", "{}") == Right(()))
    assert(s.putArtifact("sha256:a", "{}") == Right(()) && s.putArtifact("sha256:a", "{}") == Right(()))
    assert(s.register(GilEntry(iid, Instant.now(), Map("project" -> "agent"))).left.exists(_.contains("principal")))
  }
