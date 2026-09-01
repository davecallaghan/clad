package clad.evaluation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import clad.core.*
import clad.evaluation.test.TestCheckers.*
import clad.evaluation.test.Generators
import clad.evaluation.test.Generators.given

class EvaluableHierarchySpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")
  val rho: PropertyId = PropertyId.unsafe("data_source_citation")

  def makeHierarchy(ent: Set[Constraint], dept: Set[Constraint], proj: Set[Constraint]): ConstraintHierarchy =
    ConstraintHierarchy.build(ent, dept, proj).toOption.get

  "EvaluableHierarchy.build" should "succeed when all constraints classified and checkers registered" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = makeHierarchy(Set(c1), Set(c2), Set.empty)

    val mc1 = MechanicalConstraint(c1, "1.0")
    val mc2 = MechanicalConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi), NeverDetects(psi))): @unchecked

    val result = EvaluableHierarchy.build(hierarchy, Set(mc1, mc2), registry)
    result shouldBe a[Right[_, _]]
  }

  it should "succeed with a mix of mechanical and procedural constraints" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = makeHierarchy(Set(c1), Set(c2), Set.empty)

    val mc = MechanicalConstraint(c1, "1.0")
    val pc = ProceduralConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi))): @unchecked

    val result = EvaluableHierarchy.build(hierarchy, Set(mc, pc), registry)
    result shouldBe a[Right[_, _]]
  }

  it should "reject when constraints are unclassified" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = makeHierarchy(Set(c1), Set(c2), Set.empty)

    val mc1 = MechanicalConstraint(c1, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi))): @unchecked

    val result = EvaluableHierarchy.build(hierarchy, Set(mc1), registry)
    result shouldBe a[Left[_, _]]
    val Left(errors) = result: @unchecked
    errors.exists(_.isInstanceOf[EvaluableHierarchy.UnclassifiedConstraints]) shouldBe true
  }

  it should "reject when mechanical constraints lack checkers" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val hierarchy = makeHierarchy(Set(c1), Set.empty, Set.empty)

    val mc = MechanicalConstraint(c1, "1.0")

    val result = EvaluableHierarchy.build(hierarchy, Set(mc), CheckerRegistry.empty)
    result shouldBe a[Left[_, _]]
    val Left(errors) = result: @unchecked
    errors.exists(_.isInstanceOf[EvaluableHierarchy.MissingCheckers]) shouldBe true
  }

  it should "succeed with empty hierarchy" in {
    val hierarchy = makeHierarchy(Set.empty, Set.empty, Set.empty)
    val result = EvaluableHierarchy.build(hierarchy, Set.empty, CheckerRegistry.empty)
    result shouldBe a[Right[_, _]]
  }

  it should "collect multiple errors" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = makeHierarchy(Set(c1), Set(c2), Set.empty)

    val mc = MechanicalConstraint(c1, "1.0")

    val result = EvaluableHierarchy.build(hierarchy, Set(mc), CheckerRegistry.empty)
    result shouldBe a[Left[_, _]]
    val Left(errors) = result: @unchecked
    errors should have length 2
  }

  "mechanicalAt" should "return only mechanical constraints at given level" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = makeHierarchy(Set(c1), Set(c2), Set.empty)

    val mc = MechanicalConstraint(c1, "1.0")
    val pc = ProceduralConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc, pc), registry): @unchecked

    eh.mechanicalAt(Level.Enterprise) shouldBe Set(mc)
    eh.mechanicalAt(Level.Department) shouldBe Set(mc)
    eh.mechanicalAt(Level.Project) shouldBe Set(mc)
  }

  "proceduralAt" should "return only procedural constraints at given level" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Prohibition(psi, Level.Department)
    val hierarchy = makeHierarchy(Set(c1), Set(c2), Set.empty)

    val mc = MechanicalConstraint(c1, "1.0")
    val pc = ProceduralConstraint(c2, "1.0")
    val Right(registry) = CheckerRegistry.build(Seq(AlwaysDetects(phi))): @unchecked
    val Right(eh) = EvaluableHierarchy.build(hierarchy, Set(mc, pc), registry): @unchecked

    eh.proceduralAt(Level.Enterprise) shouldBe empty
    eh.proceduralAt(Level.Department) shouldBe Set(pc)
    eh.proceduralAt(Level.Project) shouldBe Set(pc)
  }
