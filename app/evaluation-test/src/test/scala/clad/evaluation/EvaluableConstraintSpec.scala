package clad.evaluation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import clad.core.*
import clad.evaluation.test.Generators
import clad.evaluation.test.Generators.given

class EvaluableConstraintSpec extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")

  "MechanicalConstraint" should "wrap a Constraint with version" in {
    val c = Constraint.Obligation(phi, Level.Enterprise)
    val mc = MechanicalConstraint(c, "1.0.0")
    mc.constraint shouldBe c
    mc.version shouldBe "1.0.0"
  }

  "ProceduralConstraint" should "wrap a Constraint with version" in {
    val c = Constraint.Prohibition(phi, Level.Enterprise)
    val pc = ProceduralConstraint(c, "2.0.0")
    pc.constraint shouldBe c
    pc.version shouldBe "2.0.0"
  }

  "EvaluableConstraint" should "be a sealed hierarchy with exactly two subtypes" in {
    val c = Constraint.Obligation(phi, Level.Enterprise)
    val mc: EvaluableConstraint = MechanicalConstraint(c, "1.0")
    val pc: EvaluableConstraint = ProceduralConstraint(c, "1.0")
    mc shouldBe a[MechanicalConstraint]
    pc shouldBe a[ProceduralConstraint]
  }

  "EvidenceSet.of" should "create set from varargs" in {
    val ev = ProceduralEvidence(phi, "auditor@example.com", true, java.time.Instant.now(), "reviewed")
    val set = EvidenceSet.of(ev)
    set.forProperty(phi) shouldBe Some(ev)
  }

  "EvidenceSet.empty" should "return None for any property" in {
    EvidenceSet.empty.forProperty(phi) shouldBe None
  }

  "EvidenceSet.forProperty" should "return None for unknown property" in {
    val psi = PropertyId.unsafe("pii_in_logs")
    val ev = ProceduralEvidence(phi, "auditor@example.com", true, java.time.Instant.now(), "ok")
    val set = EvidenceSet.of(ev)
    set.forProperty(psi) shouldBe None
  }

  "Generated EvaluableConstraints" should "preserve inner constraint" in {
    forAll(Generators.genMechanicalConstraint) { mc =>
      mc.constraint.property.value should not be empty
    }
  }
