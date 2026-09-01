package clad.runtime.checkers

import clad.core.*
import clad.evaluation.*

class CompositeChecker(
  val propertyId: PropertyId,
  checkers: Seq[PropertyChecker],
  mode: MatchMode
) extends PropertyChecker:
  def check(artifact: PromptArtifact): Boolean = mode match
    case MatchMode.Any => checkers.exists(_.check(artifact))
    case MatchMode.All => checkers.forall(_.check(artifact))
