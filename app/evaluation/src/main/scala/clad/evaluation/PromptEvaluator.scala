package clad.evaluation

import clad.core.*
import java.time.Instant

object PromptEvaluator:

  def evaluate(
    artifact: PromptArtifact,
    hierarchy: EvaluableHierarchy,
    evidence: EvidenceSet,
    level: Level,
    timestamp: Instant
  ): Either[EvaluationError, PromptEvaluationReport] =
    val mechResults = evaluateMechanical(artifact, hierarchy.mechanicalAt(level), hierarchy.checkerRegistry)
    evaluateProcedural(evidence, hierarchy.proceduralAt(level)) match
      case Left(err) => Left(err)
      case Right(procResults) =>
        val allResults = mechResults ++ procResults
        Right(PromptEvaluationReport(
          level = level,
          results = allResults,
          mechanicalResults = mechResults,
          proceduralResults = procResults,
          allSatisfied = allResults.forall(_.satisfied),
          timestamp = timestamp
        ))

  private def evaluateMechanical(
    artifact: PromptArtifact,
    constraints: Set[MechanicalConstraint],
    registry: CheckerRegistry
  ): Set[ConstraintResult] =
    constraints.map { mc =>
      val checker = registry.checkers(mc.constraint.property)
      val propertyDetected = checker.check(artifact)
      val satisfied = mc.constraint match
        case _: Constraint.Obligation  => propertyDetected
        case _: Constraint.Prohibition => !propertyDetected
      ConstraintResult(
        constraint = mc.constraint,
        version = mc.version,
        satisfied = satisfied,
        detail = MechanicalDetail(propertyDetected)
      )
    }

  private def evaluateProcedural(
    evidence: EvidenceSet,
    constraints: Set[ProceduralConstraint]
  ): Either[EvaluationError, Set[ConstraintResult]] =
    val missing = constraints.filter(pc => evidence.forProperty(pc.constraint.property).isEmpty)
    if missing.nonEmpty then
      Left(EvaluationError.MissingEvidence(missing.map(_.constraint.property)))
    else
      Right(constraints.map { pc =>
        val ev = evidence.forProperty(pc.constraint.property).get
        ConstraintResult(
          constraint = pc.constraint,
          version = pc.version,
          satisfied = ev.satisfied,
          detail = ProceduralDetail(ev.attestor, ev.attestedAt, ev.rationale)
        )
      })

  sealed trait EvaluationError
  object EvaluationError:
    case class MissingEvidence(properties: Set[PropertyId]) extends EvaluationError
