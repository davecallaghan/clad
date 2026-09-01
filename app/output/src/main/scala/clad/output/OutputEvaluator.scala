package clad.output

import clad.core.*
import java.time.Instant

object OutputEvaluator:

  def evaluate(
    artifact: OutputArtifact,
    constraints: OutputConstraintSet,
    timestamp: Instant
  ): OutputEvaluationReport =
    val detResults = evaluateDeterministic(artifact, constraints.deterministic)
    val clsResults = evaluateClassifier(artifact, constraints.classifierBased)
    val compResults = evaluateComposite(artifact, constraints.composite)
    val allResults: Vector[OutputEvalResult] = detResults ++ clsResults ++ compResults
    val decision = computeDecision(allResults, constraints)
    OutputEvaluationReport(artifact, allResults, detResults, clsResults, compResults, decision, timestamp)

  private def evaluateDeterministic(
    artifact: OutputArtifact, constraints: Seq[DeterministicConstraint]
  ): Vector[DeterministicResult] =
    constraints.map { dc =>
      val detected = dc.checker.check(artifact)
      DeterministicResult(dc.property, satisfied = !detected, detected = detected)
    }.toVector

  private def evaluateClassifier(
    artifact: OutputArtifact, constraints: Seq[ClassifierConstraint]
  ): Vector[ClassifierResult] =
    constraints.map { cc =>
      val result = cc.classifier.classify(artifact)
      val decision = cc.threshold.evaluate(result.score)
      ClassifierResult(cc.property, satisfied = decision == ThresholdResult.BelowThreshold,
        result.score, cc.threshold.flagThreshold, cc.threshold.version,
        result.classifierId, result.version, decision)
    }.toVector

  private def evaluateComposite(
    artifact: OutputArtifact, constraints: Seq[CompositeConstraint]
  ): Vector[CompositeResult] =
    constraints.map { cx =>
      val detResults = evaluateDeterministic(artifact, cx.deterministic)
      val clsResults = evaluateClassifier(artifact, cx.classifierBased)
      val satisfied = cx.logic match
        case CompositeLogic.AnyFlag =>
          detResults.forall(_.satisfied) && clsResults.forall(_.satisfied)
        case CompositeLogic.AllFlag =>
          detResults.exists(_.satisfied) || clsResults.exists(_.satisfied)
      CompositeResult(cx.property, satisfied, detResults, clsResults, cx.logic)
    }.toVector

  private def computeDecision(
    results: Vector[OutputEvalResult], constraints: OutputConstraintSet
  ): PipelineDecision =
    val unsatisfied = results.filterNot(_.satisfied)
    if unsatisfied.isEmpty then PipelineDecision.Pass
    else
      val allConstraints: Seq[OutputConstraint] =
        constraints.deterministic ++ constraints.classifierBased ++ constraints.composite
      val constraintByProp = allConstraints.map(c => c.property -> c).toMap
      val criticalFailures = unsatisfied.filter { r =>
        constraintByProp.get(r.property).exists(_.riskTier == RiskTier.Critical)
      }
      if criticalFailures.nonEmpty then PipelineDecision.Block(unsatisfied)
      else PipelineDecision.Flag(unsatisfied)
