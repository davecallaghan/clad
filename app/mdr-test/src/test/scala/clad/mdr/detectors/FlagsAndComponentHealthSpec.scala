package clad.mdr.detectors

import clad.integrity.FailurePosture
import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import org.scalatest.funsuite.AnyFunSuite

class FlagsAndComponentHealthSpec extends AnyFunSuite:
  test("one flag record is a P3 compliance alert with its id and the flagged property") {
    val s = empty.copy(records = Vector(record(id(1), "roc", "pass", ago(30)),
      record(id(1), "roc", "flag", ago(20), Vector(entry("unsafe_content", satisfied = false)))))
    val a = Flags().run(s, now).fold(fail(_), identity)
    assert(a.map(x => (x.severity, x.category, x.interactionIds)) == Vector((AlertSeverity.P3Medium, AlertCategory.Compliance, Vector(id(1)))))
    assert(a.head.summary.contains("unsafe_content"))
  }

  test("flags: pass records give nothing; an alerted flag is not repeated") {
    assert(Flags().run(empty.copy(records = Vector(record(id(1), "roc", "pass"))), now).fold(fail(_), identity).isEmpty)
    val s = empty.copy(records = Vector(record(id(1), "roc", "flag")), alertedKeys = Set(s"flag:${id(1)}"))
    assert(Flags().run(s, now).fold(fail(_), identity).isEmpty)
  }

  test("component_health: clad's detector over degraded records, P2 FailClosed, P3 FailOpenFlagged, once per interaction") {
    val s = empty.copy(degraded = Vector(degraded(id(1), ago(10)), degraded(id(2), ago(9), posture = FailurePosture.FailOpenFlagged)))
    val a = ComponentHealth().run(s, now).fold(fail(_), identity)
    assert(a.map(x => (x.interactionIds, x.severity)) == Vector((Vector(id(1)), AlertSeverity.P2High), (Vector(id(2)), AlertSeverity.P3Medium)))
    assert(ComponentHealth().run(s.copy(alertedKeys = a.map(_.dedupeKey).toSet), now).fold(fail(_), identity).isEmpty)
  }
