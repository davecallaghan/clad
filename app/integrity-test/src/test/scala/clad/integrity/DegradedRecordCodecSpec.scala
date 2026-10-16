package clad.integrity

import clad.audit.Ed25519KeyManagement
import java.time.Instant
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DegradedRecordCodecSpec extends AnyFunSuite with Matchers:
  private val rec = DegradedAuditRecord(InteractionId("i-1"), Instant.parse("2026-10-15T10:00:00.5Z"),
    ComponentId.Roc, DegradedStatus.Degraded, "classifier timeout", FailurePosture.FailClosed, FailureAction.Blocked)

  test("round-trips and the supervisor signature verifies with the public key"):
    val (kms, pub) = Ed25519KeyManagement.generate("sup")
    val signed = SignedDegradedRecord.sign(rec, kms, ComponentId.Supervisor).toOption.get
    val line = DegradedRecordCodec.encode(signed)
    line should include (""""postureApplied":"FailClosed"""")
    line should include (""""actionTaken":"Blocked"""")
    val back = DegradedRecordCodec.decode(line).toOption.get
    back.record shouldBe rec
    back.signedBy shouldBe ComponentId.Supervisor
    back.onBehalfOf shouldBe ComponentId.Roc
    SignedDegradedRecord.verify(back, Ed25519KeyManagement.fromPublicPem(pub, "sup").toOption.get) shouldBe Right(true)

  test("signed bytes are clad's existing format"):
    new String(SignedDegradedRecord.signedBytes(rec), "UTF-8") shouldBe
      "i-1|2026-10-15T10:00:00.500Z|roc|classifier timeout|FailClosed|Blocked"

  test("a malformed line is a Left"):
    DegradedRecordCodec.decode("{}").isLeft shouldBe true
