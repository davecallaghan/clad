package clad.mdr

import clad.audit.{ChainAnchorStore, Ed25519KeyManagement}
import clad.core.Surface
import clad.gateway.pipeline.InputSignal
import clad.gateway.store.{Jdbc, PgStore, TestDatabase}
import clad.integrity.*
import clad.monitoring.ComponentFailure
import clad.runtime.AuditRecord
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}

class SourceSpec extends AnyFunSuite:
  private val (comp, _) = Ed25519KeyManagement.generate("c0mp0nents000001")
  private val (sup, _) = Ed25519KeyManagement.generate("5upervis0r000001")
  private val t0 = Instant.parse("2026-10-19T10:00:00Z")
  private val inside = "0192f0c0-0000-7000-8000-00000000000a"
  private val outside = "0192f0c0-0000-7000-8000-00000000000b"

  private def caddyLine(at: Instant, iid: Option[String]): String =
    val headers = iid.fold(ujson.Obj())(i => ujson.Obj("X-Clad-Interaction-Id" -> ujson.Arr(i)))
    ujson.write(ujson.Obj("ts" -> at.toEpochMilli / 1000.0, "status" -> 200, "request" -> ujson.Obj(
      "method" -> "POST", "uri" -> "/v1/openai/chat/completions", "host" -> "egress:8081", "headers" -> headers)))

  private final class Rig:
    val db = TestDatabase.fresh()
    val store = PgStore(db.writerUrl, 7243001L, ChainAnchorStore(Files.createTempDirectory("anchors")), comp)
    val log: Path = Files.createTempFile("access", ".log")
    val source = PgSource(db.readerUrl, log, Map("8081" -> "api.deepinfra.com"))

    def gil(iid: String, at: Instant): Unit =
      store.register(GilEntry(InteractionId(iid), at, Map("project" -> "agent", "principal" -> "api", "tier" -> "Critical",
        "model" -> "m"))).fold(fail(_), identity)

    def record(iid: String, at: Instant): Unit =
      store.appendLinked(prev => AuditRecord("sha256:a", Vector.empty, "sha256:c", at, prev, Some(InteractionId(iid)),
        Some("roc"), Some(Surface.Output), Some("pass")), comp).fold(fail(_), identity)

  test("the snapshot holds the rows inside the window (widened by the lookback) and none outside") {
    val rig = Rig()
    rig.gil(inside, Instant.now())
    rig.record(inside, Instant.now())
    rig.store.inputSignal(inside, InputSignal("ignore_instructions", "1", "injection", 1.0), Instant.now()).fold(fail(_), identity)
    rig.gil(outside, Instant.now().minus(Duration.ofDays(3)))
    Files.writeString(rig.log, caddyLine(Instant.now(), Some(inside)) + "\n" +
      caddyLine(Instant.now().minus(Duration.ofDays(3)), None) + "\n")
    val w = Window(Instant.now().minus(Duration.ofHours(1)), Instant.now().plusSeconds(60))
    val s = rig.source.snapshot(w, Duration.ofHours(2)).fold(fail(_), identity)
    assert(s.gil.map(_.interactionId) == Vector(inside) && s.gilIds == Set(inside, outside))
    assert(s.records.map(r => (r.interactionId, r.component, r.outcome)) == Vector((inside, "roc", Some("pass"))))
    assert(s.signals.map((i, sig, _) => (i, sig.category)) == Vector((inside, "injection")))
    assert(s.infra.map(c => (c.host, c.interactionId)) == Vector(("api.deepinfra.com", Some(inside))))
    val wider = rig.source.snapshot(w, Duration.ofDays(4)).fold(fail(_), identity)
    assert(wider.gil.size == 2 && wider.infra.size == 2, "the lookback widens every row set")
    assert(rig.source.earliest.fold(fail(_), identity).exists(_.isBefore(Instant.now().minus(Duration.ofDays(2)))))
  }

  test("a fresh store has no heartbeat; alerted keys include dedupe keys and every id a ghosts alert listed") {
    val rig = Rig()
    assert(rig.source.lastHeartbeat == Right(None))
    Jdbc.withConnection(rig.db.readerUrl) { c =>
      c.createStatement().execute("INSERT INTO mdr.alert (alert_id, raised_at, severity, taxonomy, detector, interaction_ids, " +
        "summary, due_at, dedupe_key) VALUES (gen_random_uuid(), now(), 'P1', 'governance', 'ghosts', '{x1,x2}', 's', now(), 'ghost:k')")
      c.createStatement().execute("INSERT INTO mdr.heartbeat VALUES (now(), 'ghosts')")
    }.fold(fail(_), identity)
    val s = rig.source.snapshot(Window(Instant.now().minusSeconds(60), Instant.now()), Duration.ZERO).fold(fail(_), identity)
    assert(s.alertedKeys == Set("ghost:k", "ghost:x1", "ghost:x2") && s.undelivered.size == 1)
    assert(rig.source.lastHeartbeat.exists(_.isDefined))
  }

  test("a degraded record becomes a ComponentFailure with the record's posture; the chain reads back whole") {
    val rig = Rig()
    rig.gil(inside, Instant.now())
    rig.record(inside, Instant.now())
    val rec = DegradedAuditRecord(InteractionId(inside), Instant.now(), ComponentId.Roc, DegradedStatus.Degraded, "upstream: 503",
      FailurePosture.FailClosed, FailurePosture.actionFor(FailurePosture.FailClosed))
    rig.store.degraded(SignedDegradedRecord.sign(rec, sup, ComponentId.Supervisor).fold(e => fail(e.toString), identity)).fold(fail(_), identity)
    val s = rig.source.snapshot(Window(Instant.now().minusSeconds(60), Instant.now()), Duration.ZERO).fold(fail(_), identity)
    val failures = Events.from(s).collect { case f: ComponentFailure => f }
    assert(failures.map(f => (f.failedComponent.value, f.posture, f.interactionId.map(_.value))) ==
      Vector(("roc", FailurePosture.FailClosed, Some(inside))))
    assert(rig.source.chain.map(_.size) == Right(1))
  }
