package clad.integrity

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.runtime.checkers.GovernanceCheckers
import clad.audit.*
import clad.integrity.test.*
import java.time.Instant
import scala.util.Try

class SupervisedEngineSpec extends AnyFlatSpec with Matchers:

  val supervisorKey: Array[Byte] = "supervisor-key-must-be-32-bytes!".getBytes("UTF-8")
  val supervisorKms: KeyManagementService = HmacKeyManagement(supervisorKey, "supervisor-key")
  val supervisor = GovernanceSupervisor(ComponentId.Supervisor, supervisorKms)

  def mkEngine(): GovernanceEngine =
    val phiProp = PropertyId.unsafe("phi_access_logging")
    val entObl = Constraint.Obligation(phiProp, Level.Enterprise)
    val hierarchy = ConstraintHierarchy.build(Set(entObl), Set.empty, Set.empty).toOption.get
    val mc = MechanicalConstraint(entObl, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(GovernanceCheckers.phiAccessLogging)): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc), registry): @unchecked
    val config = EngineConfig(eh, EvidenceSet.empty, Level.Enterprise)
    GovernanceEngine.build(config).toOption.get

  "SupervisedEngine" should "succeed with GIL registered and engine passing" in {
    val gil = InMemoryInteractionLog()
    val engine = mkEngine()
    val supervised = SupervisedEngine(engine, supervisor, gil, FailurePosture.FailClosed)

    val eval = supervised.evaluate("test prompt", Map("audit_logging" -> "enabled"))

    eval.gilRegistered shouldBe true
    eval.result shouldBe a[SupervisedResult.Success[?]]
    gil.count shouldBe scala.util.Success(1)
  }

  it should "register in GIL before evaluating" in {
    val gil = InMemoryInteractionLog()
    val engine = mkEngine()
    val supervised = SupervisedEngine(engine, supervisor, gil, FailurePosture.FailClosed)

    val eval = supervised.evaluate("test", Map("audit_logging" -> "enabled"))

    gil.exists(eval.interactionId) shouldBe scala.util.Success(true)
  }

  it should "return Failed with Blocked when engine throws under FailClosed" in {
    val result = supervisor.supervise(
      ComponentId.Epg, InteractionId.generate(), FailurePosture.FailClosed,
      () => throw RuntimeException("engine crashed")
    )
    result shouldBe a[SupervisedResult.Failed[?]]
    val SupervisedResult.Failed(degraded, action) = result: @unchecked
    action shouldBe FailureAction.Blocked
    degraded.signedBy shouldBe ComponentId.Supervisor
    degraded.onBehalfOf shouldBe ComponentId.Epg
  }

  it should "return Failed with ProceededFlagged under FailOpenFlagged" in {
    val result = supervisor.supervise(
      ComponentId.Epg, InteractionId.generate(), FailurePosture.FailOpenFlagged,
      () => throw RuntimeException("timeout")
    )
    val SupervisedResult.Failed(_, action) = result: @unchecked
    action shouldBe FailureAction.ProceededFlagged
  }

  it should "block immediately when GIL fails under FailClosed" in {
    val failingGil = new InteractionLog[Try]:
      def register(entry: GilEntry): Try[Unit] = scala.util.Failure(RuntimeException("GIL unavailable"))
      def exists(id: InteractionId): Try[Boolean] = scala.util.Success(false)
      def entriesBetween(start: Instant, end: Instant): Try[Vector[GilEntry]] = scala.util.Success(Vector.empty)
      def count: Try[Int] = scala.util.Success(0)

    val engine = mkEngine()
    val supervised = SupervisedEngine(engine, supervisor, failingGil, FailurePosture.FailClosed)

    val eval = supervised.evaluate("test", Map("audit_logging" -> "enabled"))

    eval.gilRegistered shouldBe false
    eval.result shouldBe a[SupervisedResult.Failed[?]]
    val SupervisedResult.Failed(_, action) = eval.result: @unchecked
    action shouldBe FailureAction.Blocked
  }

  it should "continue with gilRegistered=false when GIL fails under FailOpenFlagged" in {
    val failingGil = new InteractionLog[Try]:
      def register(entry: GilEntry): Try[Unit] = scala.util.Failure(RuntimeException("GIL down"))
      def exists(id: InteractionId): Try[Boolean] = scala.util.Success(false)
      def entriesBetween(start: Instant, end: Instant): Try[Vector[GilEntry]] = scala.util.Success(Vector.empty)
      def count: Try[Int] = scala.util.Success(0)

    val engine = mkEngine()
    val supervised = SupervisedEngine(engine, supervisor, failingGil, FailurePosture.FailOpenFlagged)

    val eval = supervised.evaluate("test", Map("audit_logging" -> "enabled"))

    eval.gilRegistered shouldBe false
    eval.result shouldBe a[SupervisedResult.Success[?]]
  }

  it should "generate unique interaction IDs per evaluation" in {
    val gil = InMemoryInteractionLog()
    val engine = mkEngine()
    val supervised = SupervisedEngine(engine, supervisor, gil, FailurePosture.FailClosed)

    val eval1 = supervised.evaluate("prompt1", Map("audit_logging" -> "enabled"))
    val eval2 = supervised.evaluate("prompt2", Map("audit_logging" -> "enabled"))

    eval1.interactionId should not be eval2.interactionId
    gil.count shouldBe scala.util.Success(2)
  }
