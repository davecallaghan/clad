package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen
import clad.core.test.Generators
import clad.core.test.Generators.given

class ConstraintHierarchySpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")
  val rho: PropertyId = PropertyId.unsafe("data_source_citation")

  // --- Smart constructor tests ---

  "ConstraintHierarchy.build" should "succeed with empty constraint sets" in {
    val result = ConstraintHierarchy.build(Set.empty, Set.empty, Set.empty)
    result shouldBe a[Right[_, _]]
  }

  it should "succeed with non-conflicting constraints at different levels" in {
    val ent = Set[Constraint](Constraint.Obligation(phi, Level.Enterprise))
    val dept = Set[Constraint](Constraint.Obligation(psi, Level.Department))
    val proj = Set[Constraint](Constraint.Prohibition(rho, Level.Project))
    val result = ConstraintHierarchy.build(ent, dept, proj)
    result shouldBe a[Right[_, _]]
  }

  it should "reject same-level contradiction: O(phi) and F(phi) at enterprise" in {
    val ent = Set[Constraint](
      Constraint.Obligation(phi, Level.Enterprise),
      Constraint.Prohibition(phi, Level.Enterprise)
    )
    val result = ConstraintHierarchy.build(ent, Set.empty, Set.empty)
    result shouldBe a[Left[_, _]]
    val Left(errors) = result: @unchecked
    errors should have length 1
    errors.head shouldBe a[ConstraintHierarchy.Contradiction]
  }

  it should "reject cross-level contradiction: O(phi) at enterprise, F(phi) at department" in {
    val ent = Set[Constraint](Constraint.Obligation(phi, Level.Enterprise))
    val dept = Set[Constraint](Constraint.Prohibition(phi, Level.Department))
    val result = ConstraintHierarchy.build(ent, dept, Set.empty)
    result shouldBe a[Left[_, _]]
  }

  it should "reject cross-level contradiction: F(phi) at enterprise, O(phi) at project" in {
    val ent = Set[Constraint](Constraint.Prohibition(phi, Level.Enterprise))
    val proj = Set[Constraint](Constraint.Obligation(phi, Level.Project))
    val result = ConstraintHierarchy.build(ent, Set.empty, proj)
    result shouldBe a[Left[_, _]]
  }

  it should "collect multiple contradictions" in {
    val ent = Set[Constraint](
      Constraint.Obligation(phi, Level.Enterprise),
      Constraint.Obligation(psi, Level.Enterprise)
    )
    val dept = Set[Constraint](
      Constraint.Prohibition(phi, Level.Department),
      Constraint.Prohibition(psi, Level.Department)
    )
    val result = ConstraintHierarchy.build(ent, dept, Set.empty)
    result shouldBe a[Left[_, _]]
    val Left(errors) = result: @unchecked
    errors should have length 2
  }

  it should "allow tightening — adding new constraints at lower levels" in {
    val ent = Set[Constraint](Constraint.Obligation(phi, Level.Enterprise))
    val dept = Set[Constraint](Constraint.Obligation(psi, Level.Department))
    val result = ConstraintHierarchy.build(ent, dept, Set.empty)
    result shouldBe a[Right[_, _]]
  }

  // --- effectiveAt tests ---

  "effectiveAt" should "return only enterprise constraints at Enterprise level" in {
    val ent = Set[Constraint](Constraint.Obligation(phi, Level.Enterprise))
    val dept = Set[Constraint](Constraint.Obligation(psi, Level.Department))
    val Right(h) = ConstraintHierarchy.build(ent, dept, Set.empty): @unchecked
    h.effectiveAt(Level.Enterprise) shouldBe ent
  }

  it should "return enterprise + department at Department level" in {
    val ent = Set[Constraint](Constraint.Obligation(phi, Level.Enterprise))
    val dept = Set[Constraint](Constraint.Obligation(psi, Level.Department))
    val Right(h) = ConstraintHierarchy.build(ent, dept, Set.empty): @unchecked
    h.effectiveAt(Level.Department) shouldBe (ent ++ dept)
  }

  it should "return all constraints at Project level" in {
    val ent = Set[Constraint](Constraint.Obligation(phi, Level.Enterprise))
    val dept = Set[Constraint](Constraint.Obligation(psi, Level.Department))
    val proj = Set[Constraint](Constraint.Prohibition(rho, Level.Project))
    val Right(h) = ConstraintHierarchy.build(ent, dept, proj): @unchecked
    h.effectiveAt(Level.Project) shouldBe (ent ++ dept ++ proj)
  }

  // --- Property-based: Monotonicity ---

  "Monotonicity" should "hold: C*(enterprise) <= C*(department) <= C*(project)" in {
    forAll(Generators.genValidHierarchy) { h =>
      val ent = h.effectiveAt(Level.Enterprise)
      val dept = h.effectiveAt(Level.Department)
      val proj = h.effectiveAt(Level.Project)
      ent.subsetOf(dept) shouldBe true
      dept.subsetOf(proj) shouldBe true
    }
  }

  // --- Property-based: Contradiction freedom ---

  "Contradiction freedom" should "hold for all valid hierarchies at all levels" in {
    forAll(Generators.genValidHierarchy) { h =>
      for level <- Level.values do
        val effective = h.effectiveAt(level)
        val obligations = effective.collect { case Constraint.Obligation(p, _) => p }
        val prohibitions = effective.collect { case Constraint.Prohibition(p, _) => p }
        obligations.toSet.intersect(prohibitions.toSet) shouldBe empty
    }
  }
