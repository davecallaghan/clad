package clad.output

import clad.core.*
import java.time.Instant

sealed trait OutputEvalResult:
  def property: PropertyId
  def satisfied: Boolean

case class DeterministicResult(
  property: PropertyId,
  satisfied: Boolean,
  detected: Boolean
) extends OutputEvalResult

case class ClassifierResult(
  property: PropertyId,
  satisfied: Boolean,
  score: Double,
  threshold: Double,
  thresholdVersion: String,
  classifierId: String,
  classifierVersion: String,
  decision: ThresholdResult
) extends OutputEvalResult

case class CompositeResult(
  property: PropertyId,
  satisfied: Boolean,
  deterministicResults: Seq[DeterministicResult],
  classifierResults: Seq[ClassifierResult],
  logic: CompositeLogic
) extends OutputEvalResult

case class OutputEvaluationReport(
  artifact: OutputArtifact,
  results: Vector[OutputEvalResult],
  deterministicResults: Vector[DeterministicResult],
  classifierResults: Vector[ClassifierResult],
  compositeResults: Vector[CompositeResult],
  decision: PipelineDecision,
  timestamp: Instant
):
  def totalCount: Int = results.size
  def satisfiedCount: Int = results.count(_.satisfied)
  def unsatisfied: Vector[OutputEvalResult] = results.filterNot(_.satisfied)
