package clad.evaluation

import clad.core.*

sealed trait EvaluableConstraint:
  def constraint: Constraint
  def version: String

case class MechanicalConstraint(
  constraint: Constraint,
  version: String
) extends EvaluableConstraint

case class ProceduralConstraint(
  constraint: Constraint,
  version: String
) extends EvaluableConstraint
