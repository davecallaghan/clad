// code/evaluation-test/src/test/scala/clad/evaluation/PromptEvaluatorSpec.scala
package clad.evaluation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.test.TestCheckers.*
import java.time.Instant

class PromptEvaluatorSpec extends AnyFlatSpec with Matchers:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")
  val rho: PropertyId = PropertyId.unsafe("data_retention")
  val now: Instant = Instant.now()

  "evaluate" should "produce results for all constraints in effective set" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = ConstraintHierarchy.build(Set(c1), Set(c2), Set.empty).toOption.get

    val mc1 = MechanicalConstraint(c1, "1.0")
    val mc2 = MechanicalConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi), NeverDetects(psi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc1, mc2), registry): @unchecked

    val Right(report) = PromptEvaluator.evaluate(PromptArtifact("test"), eh, EvidenceSet.empty, Level.Project, now): @unchecked

    report.totalCount shouldBe 2
    report.level shouldBe Level.Project
  }

  it should "evaluate at Enterprise level with only enterprise constraints" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = ConstraintHierarchy.build(Set(c1), Set(c2), Set.empty).toOption.get

    val mc1 = MechanicalConstraint(c1, "1.0")
    val mc2 = MechanicalConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi), NeverDetects(psi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc1, mc2), registry): @unchecked

    val Right(report) = PromptEvaluator.evaluate(PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, now): @unchecked

    report.totalCount shouldBe 1
  }

  it should "handle mixed mechanical and procedural constraints" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Enterprise)
    val hierarchy = ConstraintHierarchy.build(Set(c1, c2), Set.empty, Set.empty).toOption.get

    val mc = MechanicalConstraint(c1, "1.0")
    val pc = ProceduralConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc, pc), registry): @unchecked

    val evidence = EvidenceSet.of(
      ProceduralEvidence(psi, "auditor@co.com", true, now, "reviewed and clear")
    )
    val Right(report) = PromptEvaluator.evaluate(PromptArtifact("test"), eh, evidence, Level.Enterprise, now): @unchecked

    report.totalCount shouldBe 2
    report.mechanicalResults should have size 1
    report.proceduralResults should have size 1
    report.allSatisfied shouldBe true
  }

  it should "fail when procedural evidence is missing" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val hierarchy = ConstraintHierarchy.build(Set(c1), Set.empty, Set.empty).toOption.get

    val pc = ProceduralConstraint(c1, "1.0")
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(pc), CheckerRegistry.empty): @unchecked

    val result = PromptEvaluator.evaluate(PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, now)
    result shouldBe a[Left[_, _]]
    val Left(err) = result: @unchecked
    err shouldBe a[PromptEvaluator.EvaluationError.MissingEvidence]
  }

  it should "report allSatisfied = false when any constraint fails" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Obligation(psi, Level.Enterprise)
    val hierarchy = ConstraintHierarchy.build(Set(c1, c2), Set.empty, Set.empty).toOption.get

    val mc1 = MechanicalConstraint(c1, "1.0")
    val mc2 = MechanicalConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi), NeverDetects(psi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc1, mc2), registry): @unchecked

    val Right(report) = PromptEvaluator.evaluate(PromptArtifact("test"), eh, EvidenceSet.empty, Level.Enterprise, now): @unchecked

    report.allSatisfied shouldBe false
    report.unsatisfied should have size 1
    report.satisfiedCount shouldBe 1
  }

  it should "succeed with empty hierarchy" in {
    val hierarchy = ConstraintHierarchy.build(Set.empty, Set.empty, Set.empty).toOption.get
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set.empty, CheckerRegistry.empty): @unchecked

    val Right(report) = PromptEvaluator.evaluate(PromptArtifact("test"), eh, EvidenceSet.empty, Level.Project, now): @unchecked

    report.totalCount shouldBe 0
    report.allSatisfied shouldBe true
  }

  "Report partition" should "have mechanicalResults ++ proceduralResults == results" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Enterprise)
    val hierarchy = ConstraintHierarchy.build(Set(c1, c2), Set.empty, Set.empty).toOption.get

    val mc = MechanicalConstraint(c1, "1.0")
    val pc = ProceduralConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc, pc), registry): @unchecked

    val evidence = EvidenceSet.of(
      ProceduralEvidence(psi, "auditor@co.com", false, now, "found PII")
    )
    val Right(report) = PromptEvaluator.evaluate(PromptArtifact("test"), eh, evidence, Level.Enterprise, now): @unchecked

    (report.mechanicalResults ++ report.proceduralResults) shouldBe report.results
  }
