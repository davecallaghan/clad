package clad.integrity

import clad.audit.{HmacKeyManagement, KeyManagementService}
import clad.integrity.test.FailingEngine
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GovernanceSupervisorSpec extends AnyFlatSpec with Matchers:

  val supervisorKey: Array[Byte] = "supervisor-key-must-be-32-bytes!".getBytes("UTF-8")
  val kms: KeyManagementService = HmacKeyManagement(supervisorKey, "supervisor-key")
  val supervisor = GovernanceSupervisor(ComponentId.Supervisor, kms)
  val interactionId: InteractionId = InteractionId("test-interaction-1")

  "GovernanceSupervisor" should "return Success when evaluation succeeds" in {
    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => 42
    )

    result match
      case SupervisedResult.Success(value) =>
        value shouldBe 42
      case SupervisedResult.Failed(_, _) =>
        fail("Expected Success but got Failed")
  }

  it should "handle complex types like Map" in {
    val testMap = Map("key1" -> "value1", "key2" -> "value2")
    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => testMap
    )

    result match
      case SupervisedResult.Success(value) =>
        value shouldBe testMap
      case SupervisedResult.Failed(_, _) =>
        fail("Expected Success but got Failed")
  }

  it should "handle complex types like List" in {
    val testList = List(1, 2, 3, 4, 5)
    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => testList
    )

    result match
      case SupervisedResult.Success(value) =>
        value shouldBe testList
      case SupervisedResult.Failed(_, _) =>
        fail("Expected Success but got Failed")
  }

  it should "return Failed with Blocked action for FailClosed posture" in {
    val engine = FailingEngine("Test failure")
    engine.alwaysFail()

    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => engine.evaluate(42)
    )

    result match
      case SupervisedResult.Failed(degraded, action) =>
        action shouldBe FailureAction.Blocked
        degraded.record.postureApplied shouldBe FailurePosture.FailClosed
        degraded.record.actionTaken shouldBe FailureAction.Blocked
      case SupervisedResult.Success(_) =>
        fail("Expected Failed but got Success")
  }

  it should "return Failed with ProceededFlagged action for FailOpenFlagged posture" in {
    val engine = FailingEngine("Test failure")
    engine.alwaysFail()

    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailOpenFlagged,
      () => engine.evaluate(42)
    )

    result match
      case SupervisedResult.Failed(degraded, action) =>
        action shouldBe FailureAction.ProceededFlagged
        degraded.record.postureApplied shouldBe FailurePosture.FailOpenFlagged
        degraded.record.actionTaken shouldBe FailureAction.ProceededFlagged
      case SupervisedResult.Success(_) =>
        fail("Expected Failed but got Success")
  }

  it should "return Failed with ProceededSilent action for FailOpen posture" in {
    val engine = FailingEngine("Test failure")
    engine.alwaysFail()

    val result = supervisor.supervise(
      ComponentId.Roc,
      interactionId,
      FailurePosture.FailOpen,
      () => engine.evaluate(42)
    )

    result match
      case SupervisedResult.Failed(degraded, action) =>
        action shouldBe FailureAction.ProceededSilent
        degraded.record.postureApplied shouldBe FailurePosture.FailOpen
        degraded.record.actionTaken shouldBe FailureAction.ProceededSilent
        degraded.record.component shouldBe ComponentId.Roc
      case SupervisedResult.Success(_) =>
        fail("Expected Failed but got Success")
  }

  it should "create degraded record with correct signatures" in {
    val engine = FailingEngine("Test failure")
    engine.alwaysFail()

    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => engine.evaluate(42)
    )

    result match
      case SupervisedResult.Failed(degraded, _) =>
        degraded.signedBy shouldBe ComponentId.Supervisor
        degraded.onBehalfOf shouldBe ComponentId.Epg
        degraded.signature.keyId shouldBe "supervisor-key"
      case SupervisedResult.Success(_) =>
        fail("Expected Failed but got Success")
  }

  it should "include interactionId and DegradedStatus in degraded record" in {
    val engine = FailingEngine("Test failure")
    engine.alwaysFail()

    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => engine.evaluate(42)
    )

    result match
      case SupervisedResult.Failed(degraded, _) =>
        degraded.record.interactionId shouldBe interactionId
        degraded.record.status shouldBe DegradedStatus.Degraded
      case SupervisedResult.Success(_) =>
        fail("Expected Failed but got Success")
  }

  it should "include exception class name and message in failure reason" in {
    val engine = FailingEngine("Custom failure message")
    engine.alwaysFail()

    val result = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => engine.evaluate(42)
    )

    result match
      case SupervisedResult.Failed(degraded, _) =>
        degraded.record.failureReason should include("RuntimeException")
        degraded.record.failureReason should include("Custom failure message")
      case SupervisedResult.Success(_) =>
        fail("Expected Failed but got Success")
  }

  it should "be generic over A (Int, List, Map all work)" in {
    // Int
    val intResult = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => 42
    )
    intResult match
      case SupervisedResult.Success(value) => value shouldBe 42
      case _ => fail("Expected Success for Int")

    // List
    val listResult = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => List(1, 2, 3)
    )
    listResult match
      case SupervisedResult.Success(value) => value shouldBe List(1, 2, 3)
      case _ => fail("Expected Success for List")

    // Map
    val mapResult = supervisor.supervise(
      ComponentId.Epg,
      interactionId,
      FailurePosture.FailClosed,
      () => Map("a" -> 1)
    )
    mapResult match
      case SupervisedResult.Success(value) => value shouldBe Map("a" -> 1)
      case _ => fail("Expected Success for Map")
  }
