package clad.core

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TensionSpec extends AnyFlatSpec with Matchers:

  val phi: PropertyId = PropertyId.unsafe("hipaa_disclaimer")
  val psi: PropertyId = PropertyId.unsafe("pii_in_logs")

  "TensionDetector.detectAnnotationTensions" should "detect P_meta at higher level overridden by F at lower level" in {
    val annotations = Map(
      Level.Enterprise -> Set(GovernanceAnnotation(phi, Level.Enterprise, "reviewed"))
    )
    val ent = Set.empty[Constraint]
    val dept = Set[Constraint](Constraint.Prohibition(phi, Level.Department))
    val Right(hierarchy) = ConstraintHierarchy.build(ent, dept, Set.empty): @unchecked

    val tensions = TensionDetector.detectAnnotationTensions(annotations, hierarchy)
    tensions should have length 1
    tensions.head.kind shouldBe TensionKind.AnnotationOverridden
  }

  it should "detect P_meta at higher level overridden by O at lower level" in {
    val annotations = Map(
      Level.Enterprise -> Set(GovernanceAnnotation(phi, Level.Enterprise, "reviewed"))
    )
    val dept = Set[Constraint](Constraint.Obligation(phi, Level.Department))
    val Right(hierarchy) = ConstraintHierarchy.build(Set.empty, dept, Set.empty): @unchecked

    val tensions = TensionDetector.detectAnnotationTensions(annotations, hierarchy)
    tensions should have length 1
    tensions.head.kind shouldBe TensionKind.AnnotationOverridden
  }

  it should "return empty when no annotation is overridden" in {
    val annotations = Map(
      Level.Enterprise -> Set(GovernanceAnnotation(phi, Level.Enterprise, "reviewed"))
    )
    val Right(hierarchy) = ConstraintHierarchy.build(Set.empty, Set.empty, Set.empty): @unchecked

    val tensions = TensionDetector.detectAnnotationTensions(annotations, hierarchy)
    tensions shouldBe empty
  }

  it should "not flag annotation at same level as constraint" in {
    val annotations = Map(
      Level.Department -> Set(GovernanceAnnotation(phi, Level.Department, "reviewed"))
    )
    val dept = Set[Constraint](Constraint.Prohibition(phi, Level.Department))
    val Right(hierarchy) = ConstraintHierarchy.build(Set.empty, dept, Set.empty): @unchecked

    val tensions = TensionDetector.detectAnnotationTensions(annotations, hierarchy)
    tensions shouldBe empty
  }

  "TensionDetector.detectCrossDomainTensions" should "detect same property from different domains" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Obligation(phi, Level.Department)
    val domainOf: Constraint => Domain = {
      case c if c.level == Level.Enterprise => Domain("safety")
      case _ => Domain("legal")
    }

    val tensions = TensionDetector.detectCrossDomainTensions(Set(c1, c2), domainOf)
    tensions should have length 1
    tensions.head.kind shouldBe TensionKind.CrossDomain
  }

  it should "return empty when all constraints on same property are from same domain" in {
    val c1 = Constraint.Obligation(phi, Level.Enterprise)
    val c2 = Constraint.Obligation(phi, Level.Department)
    val domainOf: Constraint => Domain = _ => Domain("safety")

    val tensions = TensionDetector.detectCrossDomainTensions(Set(c1, c2), domainOf)
    tensions shouldBe empty
  }
