package clad.evaluation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import java.time.Instant

class SemanticIntentSpec extends AnyFlatSpec with Matchers:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")
  val now: Instant = Instant.now()

  val intent = SemanticIntent(
    id = "SI-001",
    description = "All prompts must comply with HIPAA requirements",
    level = Level.Enterprise,
    domain = Domain("compliance")
  )

  val mc = MechanicalConstraint(
    Constraint.Obligation(phi, Level.Enterprise), "1.0"
  )
  val pc = ProceduralConstraint(
    Constraint.Prohibition(psi, Level.Enterprise), "1.0"
  )

  "SemanticIntent" should "not be a Constraint or EvaluableConstraint" in {
    intent shouldNot be(a[Constraint])
    intent shouldNot be(a[EvaluableConstraint])
  }

  "Decomposition" should "link intent to operational constraints" in {
    val decomp = Decomposition(
      intent = intent,
      mechanical = Set(mc),
      procedural = Set(pc),
      soundness = SoundnessState.Claimed("expert@co.com", now),
      version = "1.0"
    )
    decomp.allOperational shouldBe Set(mc, pc)
    decomp.mechanical should have size 1
    decomp.procedural should have size 1
  }

  "SoundnessState.Claimed" should "carry attestor and timestamp" in {
    val s = SoundnessState.Claimed("expert@co.com", now)
    s.attestor shouldBe "expert@co.com"
  }

  "SoundnessState.NoCounterexampleFound" should "carry last tested timestamp" in {
    val s = SoundnessState.NoCounterexampleFound(now)
    s.lastTestedAt shouldBe now
  }

  "DecompositionRegistry" should "report full coverage when all intents have decompositions" in {
    val decomp = Decomposition(intent, Set(mc), Set.empty, SoundnessState.Claimed("e@co.com", now), "1.0")
    val registry = DecompositionRegistry(Map("SI-001" -> decomp))
    registry.hasFullCoverage shouldBe true
  }

  it should "report incomplete coverage when an intent has empty decomposition" in {
    val decomp = Decomposition(intent, Set.empty, Set.empty, SoundnessState.Claimed("e@co.com", now), "1.0")
    val registry = DecompositionRegistry(Map("SI-001" -> decomp))
    registry.hasFullCoverage shouldBe false
  }

  it should "extract all evaluable constraints" in {
    val decomp = Decomposition(intent, Set(mc), Set(pc), SoundnessState.Claimed("e@co.com", now), "1.0")
    val registry = DecompositionRegistry(Map("SI-001" -> decomp))
    registry.allEvaluableConstraints shouldBe Set(mc, pc)
  }

  it should "handle multiple decompositions" in {
    val intent2 = SemanticIntent("SI-002", "No PII in logs", Level.Department, Domain("security"))
    val mc2 = MechanicalConstraint(Constraint.Obligation(psi, Level.Department), "1.0")
    val d1 = Decomposition(intent, Set(mc), Set.empty, SoundnessState.Claimed("e@co.com", now), "1.0")
    val d2 = Decomposition(intent2, Set(mc2), Set.empty, SoundnessState.NoCounterexampleFound(now), "1.0")
    val registry = DecompositionRegistry(Map("SI-001" -> d1, "SI-002" -> d2))

    registry.hasFullCoverage shouldBe true
    registry.allEvaluableConstraints should have size 2
  }
