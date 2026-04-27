package clad.evaluation

import clad.core.*
import java.time.Instant

case class ConstraintResult(
  constraint: Constraint,
  version: String,
  satisfied: Boolean,
  detail: EvalDetail
)

sealed trait EvalDetail

case class MechanicalDetail(
  propertyDetected: Boolean
) extends EvalDetail

case class ProceduralDetail(
  attestor: String,
  attestedAt: Instant,
  rationale: String
) extends EvalDetail

case class PromptEvaluationReport(
  level: Level,
  results: Set[ConstraintResult],
  mechanicalResults: Set[ConstraintResult],
  proceduralResults: Set[ConstraintResult],
  allSatisfied: Boolean,
  timestamp: Instant
):
  def unsatisfied: Set[ConstraintResult] =
    results.filter(!_.satisfied)
  def satisfiedCount: Int = results.count(_.satisfied)
  def totalCount: Int = results.size
