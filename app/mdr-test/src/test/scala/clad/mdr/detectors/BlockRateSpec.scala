package clad.mdr.detectors

import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.AlertSeverity
import org.scalatest.funsuite.AnyFunSuite

class BlockRateSpec extends AnyFunSuite:
  private val detector = BlockRate(cfg)
  private def run(s: Snapshot) = detector.run(s, now).fold(fail(_), identity)
  private val baselineAt = window.start.minusSeconds(3600)

  /** n records of property p at time t, `blocked` of them blocked. */
  private def records(p: String, n: Int, blocked: Int, t: java.time.Instant): Vector[RecordRow] =
    (1 to n).map(i => if i <= blocked then record(id(i), "epg", "block", t, Vector(entry(p, satisfied = false, t)))
      else record(id(i), "epg", "pass", t, Vector(entry(p, satisfied = true, t)))).toVector

  test("baseline 0.02, window 0.10, multiple 3.0: a P3 alert for that property") {
    val s = empty.copy(records = records("contains_pii", 50, 1, baselineAt) ++ records("contains_pii", 10, 1, ago(10)))
    assert(run(s).map(x => (x.severity, x.dedupeKey.split(":")(1))) == Vector((AlertSeverity.P3Medium, "contains_pii")))
  }

  test("a property with no baseline evaluations is not alerted; a rate within the multiple is not alerted") {
    assert(run(empty.copy(records = records("contains_pii", 10, 5, ago(10)))).isEmpty)
    val s = empty.copy(records = records("contains_pii", 10, 1, baselineAt) ++ records("contains_pii", 10, 2, ago(10)))
    assert(run(s).isEmpty, "0.2 against 0.1 is within 3x")
  }

  test("ROC fallbacks count as blocks; records older than the baseline do not count") {
    val roc = (1 to 10).map(i => record(id(i), "roc", if i == 1 then "fallback:safe_response" else "pass", ago(10),
      Vector(entry("numbers_grounded", satisfied = i != 1, ago(10))))).toVector
    val tooOld = window.start.minus(detector.lookback).minusSeconds(60)
    val s = empty.copy(records = records("numbers_grounded", 100, 0, tooOld) ++ records("numbers_grounded", 50, 1, baselineAt) ++ roc)
    assert(run(s).map(_.dedupeKey.split(":")(1)) == Vector("numbers_grounded"))
  }

  test("the same blocks seen by the next, overlapping window have the same dedupe key") {
    val s = empty.copy(records = records("contains_pii", 50, 1, baselineAt) ++ records("contains_pii", 10, 1, ago(10)))
    val next = s.copy(window = Window(s.window.start.plusSeconds(cfg.intervalSeconds), s.window.end.plusSeconds(cfg.intervalSeconds)))
    assert(run(s).map(_.dedupeKey) == run(next).map(_.dedupeKey))
  }
