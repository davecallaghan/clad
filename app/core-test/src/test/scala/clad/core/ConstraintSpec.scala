package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import clad.core.test.Generators
import clad.core.test.Generators.given

class ConstraintSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")

  "Constraint.contradicts" should "detect O(φ) vs F(φ) as contradiction" in {
    val o = Constraint.Obligation(phi, Level.Enterprise)
    val f = Constraint.Prohibition(phi, Level.Enterprise)
    Constraint.contradicts(o, f) shouldBe true
  }

  it should "detect F(φ) vs O(φ) as contradiction (symmetric)" in {
    val o = Constraint.Obligation(phi, Level.Enterprise)
    val f = Constraint.Prohibition(phi, Level.Department)
    Constraint.contradicts(f, o) shouldBe true
  }

  it should "not flag O(φ) vs O(φ) as contradiction" in {
    val o1 = Constraint.Obligation(phi, Level.Enterprise)
    val o2 = Constraint.Obligation(phi, Level.Department)
    Constraint.contradicts(o1, o2) shouldBe false
  }

  it should "not flag F(φ) vs F(φ) as contradiction" in {
    val f1 = Constraint.Prohibition(phi, Level.Enterprise)
    val f2 = Constraint.Prohibition(phi, Level.Department)
    Constraint.contradicts(f1, f2) shouldBe false
  }

  it should "not flag O(φ) vs F(ψ) as contradiction (different properties)" in {
    val o = Constraint.Obligation(phi, Level.Enterprise)
    val f = Constraint.Prohibition(psi, Level.Enterprise)
    Constraint.contradicts(o, f) shouldBe false
  }

  "contradicts" should "be symmetric for all constraint pairs" in {
    forAll(Generators.genConstraint, Generators.genConstraint) { (c1, c2) =>
      Constraint.contradicts(c1, c2) shouldBe Constraint.contradicts(c2, c1)
    }
  }

  "GovernanceAnnotation" should "not be a subtype of Constraint" in {
    val annotation = GovernanceAnnotation(phi, Level.Enterprise, "reviewed, no restriction")
    annotation shouldNot be(a[Constraint])
  }
