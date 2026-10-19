package clad.mdr.detectors

import clad.gateway.config.Models
import clad.mdr.*
import clad.mdr.TestSnapshots.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import org.scalatest.funsuite.AnyFunSuite

class ConfigDriftSpec extends AnyFunSuite:
  private val models = Models("deepinfra", Map.empty, Map("big" -> "approved", "small" -> "approved", "new" -> "pending"))
  private val detector = ConfigDrift(cfg, () => Right(models))
  private def run(s: Snapshot) = detector.run(s, now).fold(fail(_), identity)
  private def kinds(a: Vector[MdrAlert]) = a.map(_.dedupeKey.split(":").last)

  test("an unapproved model is a P2 compliance alert") {
    val a = run(empty.copy(gil = Vector(gil(id(1), ago(5), meta = "model" -> "new"))))
    assert(a.map(x => (x.severity, x.category)) == Vector((AlertSeverity.P2High, AlertCategory.Compliance)))
    assert(kinds(a) == Vector("unapproved_model"))
  }

  test("temperature 1.5 against max 1.0, and max_tokens over the bound, are P2 alerts") {
    val a = run(empty.copy(gil = Vector(gil(id(1), ago(5), meta = "model" -> "big", "temperature" -> "1.5",
      "max_tokens" -> (cfg.maxTokensMax + 1).toString))))
    assert(kinds(a).toSet == Set("temperature", "max_tokens"))
  }

  test("a template's first use of another model alerts once; alternating between seen models does not") {
    val rows = Vector(gil(id(1), ago(50), meta = "model" -> "big", "template_id" -> "t"),
      gil(id(2), ago(40), meta = "model" -> "small", "template_id" -> "t"),
      gil(id(3), ago(30), meta = "model" -> "big", "template_id" -> "t"),
      gil(id(4), ago(20), meta = "model" -> "small", "template_id" -> "t"))
    val a = run(empty.copy(gil = rows))
    assert(a.map(_.dedupeKey) == Vector("config:t:small:model_changed"))
    assert(run(empty.copy(gil = rows, alertedKeys = a.map(_.dedupeKey).toSet)).isEmpty)
  }

  test("a record naming a config digest that is not in config_version is a P2 alert") {
    val s = empty.copy(records = Vector(record(id(1), "epg", "pass", ago(5), config = "sha256:x")), configDigests = Set("sha256:c"))
    assert(kinds(run(s)) == Vector("unknown_config"))
    assert(run(s.copy(configDigests = Set("sha256:x"))).isEmpty)
  }
