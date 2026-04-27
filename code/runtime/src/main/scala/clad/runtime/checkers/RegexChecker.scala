package clad.runtime.checkers

import clad.core.*
import clad.evaluation.*
import scala.util.matching.Regex

class RegexChecker(
  val propertyId: PropertyId,
  patterns: Seq[Regex],
  mode: MatchMode
) extends PropertyChecker:
  def check(artifact: PromptArtifact): Boolean = mode match
    case MatchMode.Any => patterns.exists(_.findFirstIn(artifact.content).isDefined)
    case MatchMode.All => patterns.forall(_.findFirstIn(artifact.content).isDefined)
