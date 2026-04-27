package clad.evaluation

import clad.core.*

final class EvaluableHierarchy private (
  val hierarchy: ConstraintHierarchy,
  val classifications: Map[Constraint, EvaluableConstraint],
  val checkerRegistry: CheckerRegistry
):
  def mechanicalAt(level: Level): Set[MechanicalConstraint] =
    hierarchy.effectiveAt(level)
      .map(classifications)
      .collect { case mc: MechanicalConstraint => mc }

  def proceduralAt(level: Level): Set[ProceduralConstraint] =
    hierarchy.effectiveAt(level)
      .map(classifications)
      .collect { case pc: ProceduralConstraint => pc }

object EvaluableHierarchy:
  sealed trait BuildError
  case class UnclassifiedConstraints(constraints: Set[Constraint]) extends BuildError
  case class MissingCheckers(properties: Set[PropertyId]) extends BuildError

  def build(
    hierarchy: ConstraintHierarchy,
    evaluable: Set[EvaluableConstraint],
    registry: CheckerRegistry
  ): Either[List[BuildError], EvaluableHierarchy] =
    val errors = List.newBuilder[BuildError]

    val allConstraints = hierarchy.effectiveAt(Level.Project)
    val classified = evaluable.map(_.constraint).toSet
    val unclassified = allConstraints -- classified
    if unclassified.nonEmpty then
      errors += UnclassifiedConstraints(unclassified)

    val mechanicalProps = evaluable.collect {
      case mc: MechanicalConstraint => mc.constraint.property
    }.toSet
    val registeredProps = registry.checkers.keySet
    val missingCheckers = mechanicalProps -- registeredProps
    if missingCheckers.nonEmpty then
      errors += MissingCheckers(missingCheckers)

    val errs = errors.result()
    if errs.nonEmpty then Left(errs)
    else
      val classMap = evaluable.map(ec => ec.constraint -> ec).toMap
      Right(new EvaluableHierarchy(hierarchy, classMap, registry))
