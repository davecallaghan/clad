package clad.evaluation

import clad.core.*

trait PropertyChecker:
  def propertyId: PropertyId
  def check(artifact: PromptArtifact): Boolean

case class CheckerRegistry private (checkers: Map[PropertyId, PropertyChecker])

object CheckerRegistry:
  val empty: CheckerRegistry = CheckerRegistry(Map.empty)

  def build(checkers: Seq[PropertyChecker]): Either[DuplicateChecker, CheckerRegistry] =
    val grouped = checkers.groupBy(_.propertyId)
    val dupes = grouped.filter(_._2.size > 1).keys.toSet
    if dupes.nonEmpty then Left(DuplicateChecker(dupes))
    else Right(CheckerRegistry(checkers.map(c => c.propertyId -> c).toMap))

  case class DuplicateChecker(properties: Set[PropertyId])
