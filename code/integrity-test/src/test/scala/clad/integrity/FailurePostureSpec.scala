package clad.integrity

import clad.audit.HmacKeyManagement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import java.time.Instant

class FailurePostureSpec extends AnyFunSuite with Matchers:

  test("FailurePosture has 3 values"):
    FailurePosture.values.length shouldBe 3
    FailurePosture.values should contain allOf (
      FailurePosture.FailClosed,
      FailurePosture.FailOpenFlagged,
      FailurePosture.FailOpen
    )

  test("FailureAction has 3 values"):
    FailureAction.values.length shouldBe 3
    FailureAction.values should contain allOf (
      FailureAction.Blocked,
      FailureAction.ProceededFlagged,
      FailureAction.ProceededSilent
    )

  test("actionFor maps FailClosed to Blocked"):
    FailurePosture.actionFor(FailurePosture.FailClosed) shouldBe FailureAction.Blocked

  test("actionFor maps FailOpenFlagged to ProceededFlagged"):
    FailurePosture.actionFor(FailurePosture.FailOpenFlagged) shouldBe FailureAction.ProceededFlagged

  test("actionFor maps FailOpen to ProceededSilent"):
    FailurePosture.actionFor(FailurePosture.FailOpen) shouldBe FailureAction.ProceededSilent

  test("ComponentId stores and retrieves value"):
    val componentId = ComponentId("test-component")
    componentId.value shouldBe "test-component"

  test("ComponentId has pre-defined Epg, Roc, Supervisor"):
    ComponentId.Epg.value shouldBe "epg"
    ComponentId.Roc.value shouldBe "roc"
    ComponentId.Supervisor.value shouldBe "supervisor"

  test("DegradedAuditRecord stores all failure info"):
    val interactionId = InteractionId("test-interaction")
    val timestamp = Instant.now()
    val component = ComponentId.Epg
    val status = DegradedStatus.Degraded
    val failureReason = "Network timeout"
    val postureApplied = FailurePosture.FailOpenFlagged
    val actionTaken = FailureAction.ProceededFlagged

    val record = DegradedAuditRecord(
      interactionId = interactionId,
      timestamp = timestamp,
      component = component,
      status = status,
      failureReason = failureReason,
      postureApplied = postureApplied,
      actionTaken = actionTaken
    )

    record.interactionId shouldBe interactionId
    record.timestamp shouldBe timestamp
    record.component shouldBe component
    record.status shouldBe status
    record.failureReason shouldBe failureReason
    record.postureApplied shouldBe postureApplied
    record.actionTaken shouldBe actionTaken

  test("SignedDegradedRecord.sign signs with supervisor key"):
    val kms = HmacKeyManagement("supervisor-key-must-be-32-bytes!".getBytes("UTF-8"), "supervisor-key")
    val record = DegradedAuditRecord(
      interactionId = InteractionId("test-interaction"),
      timestamp = Instant.now(),
      component = ComponentId.Epg,
      status = DegradedStatus.Degraded,
      failureReason = "Test failure",
      postureApplied = FailurePosture.FailClosed,
      actionTaken = FailureAction.Blocked
    )

    val result = SignedDegradedRecord.sign(record, kms, ComponentId.Supervisor)
    result shouldBe a[Right[_, _]]
    val signedRecord = result.toOption.get
    signedRecord.record shouldBe record
    signedRecord.signature should not be null

  test("SignedDegradedRecord has signedBy != onBehalfOf"):
    val kms = HmacKeyManagement("supervisor-key-must-be-32-bytes!".getBytes("UTF-8"), "supervisor-key")
    val record = DegradedAuditRecord(
      interactionId = InteractionId("test-interaction"),
      timestamp = Instant.now(),
      component = ComponentId.Epg,
      status = DegradedStatus.Degraded,
      failureReason = "Test failure",
      postureApplied = FailurePosture.FailClosed,
      actionTaken = FailureAction.Blocked
    )

    val result = SignedDegradedRecord.sign(record, kms, ComponentId.Supervisor)
    val signedRecord = result.toOption.get
    signedRecord.signedBy shouldBe ComponentId.Supervisor
    signedRecord.onBehalfOf shouldBe ComponentId.Epg
    signedRecord.signedBy should not be signedRecord.onBehalfOf
