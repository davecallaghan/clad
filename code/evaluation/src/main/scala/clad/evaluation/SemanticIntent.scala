package clad.evaluation

import clad.core.*
import java.time.Instant

case class SemanticIntent(
  id: String,
  description: String,
  level: Level,
  domain: Domain
)

case class Decomposition(
  intent: SemanticIntent,
  mechanical: Set[MechanicalConstraint],
  procedural: Set[ProceduralConstraint],
  soundness: SoundnessState,
  version: String
):
  def allOperational: Set[EvaluableConstraint] =
    mechanical ++ procedural

sealed trait SoundnessState

object SoundnessState:
  case class Claimed(attestor: String, attestedAt: Instant) extends SoundnessState
  case class NoCounterexampleFound(lastTestedAt: Instant) extends SoundnessState

case class DecompositionRegistry(
  decompositions: Map[String, Decomposition]
):
  def hasFullCoverage: Boolean =
    decompositions.values.forall(d => d.mechanical.nonEmpty || d.procedural.nonEmpty)
  def allEvaluableConstraints: Set[EvaluableConstraint] =
    decompositions.values.flatMap(_.allOperational).toSet
