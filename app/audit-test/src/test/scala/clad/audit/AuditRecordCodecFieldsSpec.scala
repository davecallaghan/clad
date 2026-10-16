package clad.audit

import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.InMemoryKeyManagement
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class AuditRecordCodecFieldsSpec extends AnyFunSuite with Matchers:
  private val t = Instant.parse("2026-10-15T10:00:00.120Z")
  private val rec = AuditRecord("sha256:a", Vector.empty, "sha256:c", t, None,
    Some(InteractionId("i-1")), Some("roc"), Some(Surface.Output), Some("fallback:redacted"))

  test("optional fields round-trip"):
    val signed = SignedAuditRecord.sign(rec, InMemoryKeyManagement.default).toOption.get
    val back = AuditRecordCodec.decode(AuditRecordCodec.encode(signed)).toOption.get
    back.record shouldBe rec
    back.record.digest shouldBe rec.digest

  test("a line without the new fields decodes them as None"):
    val signed = SignedAuditRecord.sign(rec.copy(component = None, surface = None, outcome = None), InMemoryKeyManagement.default).toOption.get
    val line = AuditRecordCodec.encode(signed)
      .replace(""","component":null""", "").replace(""","surface":null""", "").replace(""","outcome":null""", "")
    line.contains("component") shouldBe false
    val back = AuditRecordCodec.decode(line).toOption.get
    back.record.component shouldBe None
    back.record.surface shouldBe None
    back.record.outcome shouldBe None
