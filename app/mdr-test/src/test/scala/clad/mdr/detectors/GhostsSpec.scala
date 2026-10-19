package clad.mdr.detectors

import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.AlertSeverity
import org.scalatest.funsuite.AnyFunSuite

class GhostsSpec extends AnyFunSuite:
  private val ghosts = Ghosts(cfg)
  private def run(s: Snapshot) = ghosts.run(s, now).fold(fail(_), identity)

  test("an infra call whose id has no GIL entry is a P1 bypass alert listing it") {
    val a = run(empty.copy(infra = Vector(infra(ago(10), Some(id(1))))))
    assert(a.map(x => (x.severity, x.interactionIds)) == Vector((AlertSeverity.P1Critical, Vector(id(1)))))
    assert(a.head.detector == "ghosts" && a.head.summary.contains("bypass"))
  }

  test("an infra call with no id is a P1 alert from ghosts listing infra@<ts>, with the timestamp and host in the text") {
    val a = run(empty.copy(infra = Vector(infra(ago(10), None))))
    assert(a.map(_.interactionIds) == Vector(Vector(s"infra@${ago(10)}")) && a.head.severity == AlertSeverity.P1Critical)
    assert(a.head.summary.contains(ago(10).toString) && a.head.summary.contains("api.deepinfra.com"))
  }

  test("a GIL entry inside the in-flight grace is not a ghost; past it, it is P1; with a degraded record, P2") {
    val young = empty.copy(gil = Vector(gil(id(2), ago(30))), gilIds = Set(id(2)))
    assert(run(young).isEmpty, "30 s old with grace 120")
    val old = young.copy(gil = Vector(gil(id(2), ago(200))))
    assert(run(old).map(x => (x.severity, x.interactionIds)) == Vector((AlertSeverity.P1Critical, Vector(id(2)))))
    val failed = old.copy(degraded = Vector(degraded(id(2), ago(190))))
    assert(run(failed).map(_.severity) == Vector(AlertSeverity.P2High))
  }

  test("a delivered call with an EPG record but no ROC record is a ghost of kind no_roc; with a ROC record, none") {
    val s = empty.copy(gil = Vector(gil(id(3), ago(200))), gilIds = Set(id(3)), infra = Vector(infra(ago(199), Some(id(3)))),
      records = Vector(record(id(3), "epg", "pass", ago(199))))
    assert(run(s).map(x => (x.dedupeKey.split(":")(1), x.interactionIds)) == Vector(("no_roc", Vector(id(3)))))
    assert(run(s.copy(records = s.records :+ record(id(3), "roc", "pass", ago(198)))).isEmpty)
  }

  test("an id already alerted (ghost:<id> in alertedKeys) is not alerted again") {
    val s = empty.copy(infra = Vector(infra(ago(10), Some(id(1))), infra(ago(9), None)))
    assert(run(s.copy(alertedKeys = Set(s"ghost:${id(1)}", s"ghost:infra@${ago(9)}"))).isEmpty)
  }
