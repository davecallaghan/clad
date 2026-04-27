package clad.runtime.test

import clad.core.*
import clad.evaluation.*
import clad.runtime.checkers.GovernanceCheckers
import java.time.Instant

object SampleHierarchies:
  val phiProp: PropertyId = PropertyId.unsafe("phi_access_logging")
  val piiProp: PropertyId = PropertyId.unsafe("pii_detected")
  val encProp: PropertyId = PropertyId.unsafe("encryption_in_transit")
  val baaProp: PropertyId = PropertyId.unsafe("baa_current")

  val entObligation: Constraint = Constraint.Obligation(phiProp, Level.Enterprise)
  val entProhibition: Constraint = Constraint.Prohibition(piiProp, Level.Enterprise)
  val deptObligation: Constraint = Constraint.Obligation(encProp, Level.Department)
  val projObligation: Constraint = Constraint.Obligation(baaProp, Level.Project)

  val hierarchy: ConstraintHierarchy =
    ConstraintHierarchy.build(Set(entObligation, entProhibition), Set(deptObligation), Set(projObligation)).toOption.get

  val mechanicalConstraints: Set[EvaluableConstraint] = Set(
    MechanicalConstraint(entObligation, "1.0"),
    MechanicalConstraint(entProhibition, "1.0"),
    MechanicalConstraint(deptObligation, "1.0"),
    MechanicalConstraint(projObligation, "1.0")
  )

  val Right(checkerRegistry) =
    CheckerRegistry.build(Seq(
      GovernanceCheckers.phiAccessLogging, GovernanceCheckers.piiDetected,
      GovernanceCheckers.encryptionInTransit, GovernanceCheckers.baaCurrent
    )): @unchecked

  val Right(evaluableHierarchy) =
    EvaluableHierarchy.build(hierarchy, mechanicalConstraints, checkerRegistry): @unchecked

  // Mixed hierarchy with one procedural constraint
  val reviewProp: PropertyId = PropertyId.unsafe("bias_assessment_completed")
  val procConstraint: Constraint = Constraint.Obligation(reviewProp, Level.Enterprise)
  val mixedHierarchy: ConstraintHierarchy =
    ConstraintHierarchy.build(Set(entObligation, procConstraint), Set.empty, Set.empty).toOption.get
  val mixedEvaluable: Set[EvaluableConstraint] = Set(
    MechanicalConstraint(entObligation, "1.0"), ProceduralConstraint(procConstraint, "1.0")
  )
  val Right(mixedCheckerRegistry) =
    CheckerRegistry.build(Seq(GovernanceCheckers.phiAccessLogging)): @unchecked
  val Right(mixedEvaluableHierarchy) =
    EvaluableHierarchy.build(mixedHierarchy, mixedEvaluable, mixedCheckerRegistry): @unchecked

  def sampleEvidence(satisfied: Boolean = true): EvidenceSet =
    EvidenceSet.of(ProceduralEvidence(
      reviewProp, "auditor@example.com", satisfied,
      Instant.parse("2026-04-22T12:00:00Z"), "Bias assessment reviewed"
    ))
