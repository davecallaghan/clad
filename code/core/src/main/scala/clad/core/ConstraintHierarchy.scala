package clad.core

final case class LevelConstraints private (
  level: Level,
  constraints: Set[Constraint]
)

object LevelConstraints:
  def apply(level: Level, constraints: Set[Constraint]): Either[String, LevelConstraints] =
    val mismatched = constraints.filter(_.level != level)
    if mismatched.nonEmpty then Left(s"Constraints at wrong level: $mismatched")
    else Right(new LevelConstraints(level, constraints))

  private[core] def unsafeApply(level: Level, constraints: Set[Constraint]): LevelConstraints =
    new LevelConstraints(level, constraints)

final class ConstraintHierarchy private (
  val enterprise: LevelConstraints,
  val department: LevelConstraints,
  val project: LevelConstraints
):
  def effectiveAt(level: Level): Set[Constraint] = level match
    case Level.Enterprise => enterprise.constraints
    case Level.Department => enterprise.constraints ++ department.constraints
    case Level.Project    => enterprise.constraints ++ department.constraints ++ project.constraints

object ConstraintHierarchy:
  sealed trait BuildError
  case class Contradiction(c1: Constraint, c2: Constraint) extends BuildError

  def build(
    enterprise: Set[Constraint],
    department: Set[Constraint],
    project: Set[Constraint]
  ): Either[List[BuildError], ConstraintHierarchy] =
    val errors = List.newBuilder[BuildError]

    findContradictions(enterprise).foreach(errors += _)

    val deptEffective = enterprise ++ department
    findContradictions(deptEffective).foreach(errors += _)

    val projEffective = deptEffective ++ project
    findContradictions(projEffective).foreach(errors += _)

    val errs = errors.result().distinct
    if errs.nonEmpty then Left(errs)
    else Right(new ConstraintHierarchy(
      LevelConstraints.unsafeApply(Level.Enterprise, enterprise),
      LevelConstraints.unsafeApply(Level.Department, department),
      LevelConstraints.unsafeApply(Level.Project, project)
    ))

  private def findContradictions(cs: Set[Constraint]): List[Contradiction] =
    val obligations = cs.collect { case o: Constraint.Obligation => o.property -> o }.toMap
    val prohibitions = cs.collect { case f: Constraint.Prohibition => f.property -> f }.toMap
    val contradicted = obligations.keySet.intersect(prohibitions.keySet)
    contradicted.toList.map(phi => Contradiction(obligations(phi), prohibitions(phi)))
