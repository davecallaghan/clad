package clad.mdr.detectors

import clad.audit.*
import clad.core.Surface
import clad.gateway.keys.Keys
import clad.integrity.InteractionId
import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.AlertSeverity
import clad.runtime.AuditRecord
import org.scalatest.funsuite.AnyFunSuite
import java.nio.file.{Files, Path}
import java.time.Duration

class ChainHealthSpec extends AnyFunSuite:
  private final class ChainSource(val records: Vector[SignedAuditRecord]) extends Source:
    def snapshot(w: Window, lookback: Duration) = Right(empty)
    def lastHeartbeat = Right(None)
    def earliest = Right(None)
    def chain = Right(records)

  private final class Rig(n: Int):
    val keys: Path = Files.createTempDirectory("keys")
    Keys.generate(keys, "components")
    val kms = Keys.load(keys, "components").fold(fail(_), identity)
    val anchors: Path = Files.createTempDirectory("anchors")
    val chain = (1 to n).foldLeft(SignedAuditChain.empty) { (c, i) =>
      c.append(AuditRecord("sha256:a", Vector.empty, "sha256:c", ago(i), None, Some(InteractionId(id(i))), Some("roc"),
        Some(Surface.Output), Some("pass")), kms).fold(e => fail(e.toString), identity)
    }.records
    ChainAnchorStore(anchors).write(ChainAnchor.sign(ChainAnchor.forChain(chain, now), kms).fold(e => fail(e.toString), identity)).get
    def run(records: Vector[SignedAuditRecord] = chain, s: Snapshot = empty) =
      ChainHealth(cfg, ChainSource(records), keys, anchors).run(s, now).fold(fail(_), identity)

  test("a whole, signed, anchored chain gives no alert; a chain longer than its anchor is consistent") {
    val rig = Rig(3)
    assert(rig.run().isEmpty)
    val longer = rig.chain :+ SignedAuditRecord.sign(rig.chain.last.record.copy(previousDigest = Some(rig.chain.last.record.digest)),
      rig.kms).fold(e => fail(e.toString), identity)
    assert(rig.run(longer).isEmpty)
  }

  test("a tampered line is a P1 integrity alert") {
    val rig = Rig(3)
    val tampered = rig.chain.updated(1, rig.chain(1).copy(record = rig.chain(1).record.copy(outcome = Some("block"))))
    val a = rig.run(tampered)
    assert(a.map(_.severity) == Vector(AlertSeverity.P1Critical) && a.head.dedupeKey.startsWith("chain:"))
  }

  test("a chain shorter than its anchor (truncation) or whose record differs from the anchor's head is P1") {
    val rig = Rig(3)
    assert(rig.run(rig.chain.dropRight(1)).exists(_.summary.contains("truncation")))
    val other = Rig(3)
    assert(ChainHealth(cfg, ChainSource(other.chain), rig.keys, rig.anchors).run(empty, now).fold(fail(_), identity)
      .map(_.severity) == Vector(AlertSeverity.P1Critical))
  }

  test("a degraded ratio of 0.1 against 0.05 is a P3 alert; at 0.04 none") {
    val rig = Rig(1)
    val gil10 = (1 to 10).map(i => gil(id(i), ago(i))).toVector
    val a = rig.run(s = empty.copy(gil = gil10, degraded = Vector(degraded(id(1), ago(1)))))
    assert(a.map(x => (x.severity, x.dedupeKey.takeWhile(_ != ':'))) == Vector((AlertSeverity.P3Medium, "degraded_ratio")))
    val gil25 = (1 to 25).map(i => gil(id(i), ago(i))).toVector
    assert(rig.run(s = empty.copy(gil = gil25, degraded = Vector(degraded(id(1), ago(1))))).isEmpty)
  }

  test("the same degraded records seen by the next, overlapping window have the same dedupe key") {
    val rig = Rig(1)
    val s = empty.copy(gil = (1 to 10).map(i => gil(id(i), ago(i))).toVector, degraded = Vector(degraded(id(1), ago(1))))
    val next = s.copy(window = Window(s.window.start.plusSeconds(cfg.intervalSeconds), s.window.end.plusSeconds(cfg.intervalSeconds)))
    assert(rig.run(s = s).map(_.dedupeKey) == rig.run(s = next).map(_.dedupeKey))
  }
