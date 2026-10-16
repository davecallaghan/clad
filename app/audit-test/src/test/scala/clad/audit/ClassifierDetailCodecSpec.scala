package clad.audit

import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.InMemoryKeyManagement
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ClassifierDetailCodecSpec extends AnyFunSuite with Matchers:
  test("a classifier entry round-trips and its class is part of the digest"):
    val t = Instant.parse("2026-10-15T10:00:00Z")
    val e = AuditEntry(Constraint.Prohibition(PropertyId.unsafe("specialized_advice"), Level.Enterprise), "1",
      EvaluabilityClass.Classifier, satisfied = false,
      ClassifierDetail("llama_guard", "4", 0.91, 0.5, "1"), t)
    val r = AuditRecord("sha256:a", Vector(e), "sha256:c", t, None)
    val back = AuditRecordCodec.decode(AuditRecordCodec.encode(SignedAuditRecord.sign(r, InMemoryKeyManagement.default).toOption.get)).toOption.get
    back.record shouldBe r
    r.digest should not be r.copy(entries = Vector(e.copy(evaluabilityClass = EvaluabilityClass.Mechanical))).digest
