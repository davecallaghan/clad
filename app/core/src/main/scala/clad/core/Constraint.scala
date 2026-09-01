package clad.core

sealed trait Constraint:
  def property: PropertyId
  def level: Level

object Constraint:
  case class Obligation(property: PropertyId, level: Level) extends Constraint
  case class Prohibition(property: PropertyId, level: Level) extends Constraint

  def contradicts(c1: Constraint, c2: Constraint): Boolean =
    (c1, c2) match
      case (Obligation(p1, _), Prohibition(p2, _)) => p1 == p2
      case (Prohibition(p1, _), Obligation(p2, _)) => p1 == p2
      case _ => false
