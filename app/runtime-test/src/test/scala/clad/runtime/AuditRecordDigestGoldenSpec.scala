package clad.runtime

import clad.core.*
import clad.evaluation.*
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AuditRecordDigestGoldenSpec extends AnyFunSuite with Matchers:
  private val t0 = Instant.parse("2026-10-15T10:00:00Z")
  private def entry(p: String, v: String, at: Instant) =
    AuditEntry(Constraint.Obligation(PropertyId.unsafe(p), Level.Enterprise), v, EvaluabilityClass.Mechanical, true, MechanicalDetail(true), at)

  test("legacy record (no optional fields) keeps its digest"):
    AuditRecord("sha256:artifact", Vector(entry("test_property", "1.0", t0)), "sha256:config", t0, None).digest shouldBe
      "sha256:17b2b85475834db1d93f4ed9d29e82061329cbba11a732188ab4b806e1e4228d"

  test("record with only an interaction id keeps its digest"):
    AuditRecord("sha256:artifact", Vector.empty, "sha256:config", t0, None,
      Some(InteractionId("0192f0c0-0000-7000-8000-000000000001"))).digest shouldBe
      "sha256:60dc5785944fd65643cdec88e81e925eb82bc129eab41a5a2f1ce52fab07a0cc"

  test("record with every optional field"):
    val t = Instant.parse("2026-10-15T10:00:00.120Z")
    AuditRecord("sha256:artifact",
      Vector(entry("grounding_instruction", "1", t), entry("contains_pii", "1", t)),
      "sha256:config", t, Some("sha256:prev"),
      Some(InteractionId("0192f0c0-0000-7000-8000-000000000001")),
      component = Some("epg"), surface = Some(Surface.Prompt), outcome = Some("pass")).digest shouldBe
      "sha256:bdec21c1c4465ae6377c9d9cfd39222fda58f0aa8123e403b1f46198f034c875"
