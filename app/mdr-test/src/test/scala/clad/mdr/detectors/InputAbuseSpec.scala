package clad.mdr.detectors

import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import org.scalatest.funsuite.AnyFunSuite

class InputAbuseSpec extends AnyFunSuite:
  private val detector = InputAbuse(cfg)
  private def run(s: Snapshot) = detector.run(s, now).fold(fail(_), identity)
  private val gils = (1 to 3).map(i => gil(id(i), ago(i * 10), principal = "api:mallory")).toVector

  test("three injection signals from one principal within the window are one P2 adversarial alert") {
    val a = run(empty.copy(gil = gils, signals = (1 to 3).map(i => injection(id(i), ago(i * 10))).toVector))
    assert(a.map(x => (x.severity, x.category, x.interactionIds.toSet)) ==
      Vector((AlertSeverity.P2High, AlertCategory.Adversarial, Set(id(1), id(2), id(3)))))
  }

  test("three signals spread over two principals, or outside the window, are no alert") {
    val split = gils.updated(2, gil(id(3), ago(30), principal = "api:alice"))
    assert(run(empty.copy(gil = split, signals = (1 to 3).map(i => injection(id(i), ago(i * 10))).toVector)).isEmpty)
    val spread = (1 to 3).map(i => injection(id(i), ago(i * (cfg.injectionWindowSeconds + 1)))).toVector
    assert(run(empty.copy(gil = gils, signals = spread)).isEmpty)
  }

  test("the same burst already alerted is not alerted again; other categories do not count") {
    val s = empty.copy(gil = gils, signals = (1 to 3).map(i => injection(id(i), ago(i * 10))).toVector)
    val key = run(s).head.dedupeKey
    assert(run(s.copy(alertedKeys = Set(key))).isEmpty)
    val pii = s.signals.map((i, sig, at) => (i, sig.copy(category = "pii"), at))
    assert(run(s.copy(signals = pii)).isEmpty)
  }
