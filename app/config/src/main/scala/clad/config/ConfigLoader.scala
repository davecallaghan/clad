package clad.config

import upickle.default.*
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.runtime.checkers.*
import java.nio.file.{Files, Path}

object ConfigLoader:
  private given ReadWriter[AgentConfig] = macroRW
  private given ReadWriter[ConstraintConfig] = macroRW
  private given ReadWriter[ConstraintHierarchyConfig] = macroRW
  private given ReadWriter[CheckerBinding] = macroRW
  private given ReadWriter[OutputConstraintConfig] = macroRW
  private given ReadWriter[ThresholdConfig] = macroRW
  private given ReadWriter[FailurePostureConfig] = macroRW
  private given ReadWriter[GovernanceConfig] = macroRW

  sealed trait ConfigError
  case class ParseError(message: String) extends ConfigError
  case class ValidationError(field: String, message: String) extends ConfigError
  case class CheckerNotFound(property: String, checkerType: String) extends ConfigError

  def loadFromString(json: String): Either[List[ConfigError], GovernanceConfig] =
    try Right(read[GovernanceConfig](json))
    catch case e: Exception => Left(List(ParseError(e.getMessage)))

  def loadFromFile(path: Path): Either[List[ConfigError], GovernanceConfig] =
    try
      val json = new String(Files.readAllBytes(path), "UTF-8")
      loadFromString(json)
    catch case e: Exception => Left(List(ParseError(s"Failed to read file: ${e.getMessage}")))

  def buildAgents(config: GovernanceConfig): Seq[Agent] =
    config.agents.map { ac =>
      Agent(
        ac.id, ac.name,
        ac.authorizedDomains.map(Domain(_)).toSet,
        ac.authorizedLevels.map(parseLevel).toSet
      )
    }

  def buildEngine(config: GovernanceConfig): Either[List[ConfigError], GovernanceEngine] =
    val errors = List.newBuilder[ConfigError]

    val allConstraintConfigs = config.constraints.enterprise ++ config.constraints.department ++ config.constraints.project

    // Each parsed constraint is kept paired with the config entry it came from. Looking
    // the entry back up by property name alone returned the first match in
    // enterprise-then-department-then-project order, so a project-level override of an
    // enterprise property silently took the enterprise entry's version and evaluability
    // — reclassifying a mechanical check as procedural, or stamping the wrong version
    // into every audit record for that constraint. Constraint itself is the right key:
    // Obligation and Prohibition are distinct values, so (property, level, type) is
    // exactly a Constraint.
    val parsed: Seq[(Constraint, ConstraintConfig)] = allConstraintConfigs.flatMap { cc =>
      parseLevelOpt(cc.level) match
        case Some(level) =>
          val prop = PropertyId.unsafe(cc.property)
          cc.constraintType.toLowerCase match
            case "obligation" => Some(Constraint.Obligation(prop, level) -> cc)
            case "prohibition" => Some(Constraint.Prohibition(prop, level) -> cc)
            case other =>
              errors += ValidationError("constraintType", s"Unknown type: $other")
              None
        case None =>
          errors += ValidationError("level", s"Unknown level: ${cc.level}")
          None
    }

    // Two entries for one constraint that disagree about version or evaluability have no
    // defensible resolution, and building a Map would silently keep whichever came last.
    parsed.groupBy(_._1).foreach { case (constraint, entries) =>
      val variants = entries.map(e => (e._2.version, e._2.evaluability.toLowerCase)).distinct
      if variants.sizeIs > 1 then
        errors += ValidationError(
          "constraints",
          s"${constraint.property.value} at ${constraint.level} is declared more than " +
            s"once with conflicting version/evaluability: " +
            variants.map((v, e) => s"$v/$e").mkString(", ")
        )
    }

    val configFor: Map[Constraint, ConstraintConfig] = parsed.toMap
    val constraints = parsed.map(_._1)

    val entConstraints = constraints.filter(_.level == Level.Enterprise).toSet
    val deptConstraints = constraints.filter(_.level == Level.Department).toSet
    val projConstraints = constraints.filter(_.level == Level.Project).toSet

    val hierarchyResult = ConstraintHierarchy.build(entConstraints, deptConstraints, projConstraints)
    val hierarchy = hierarchyResult match
      case Right(h) => h
      case Left(errs) =>
        errs.foreach(e => errors += ValidationError("hierarchy", e.toString))
        return Left(errors.result())

    val checkers = config.checkers.flatMap { cb =>
      buildChecker(cb) match
        case Right(checker) => Some(checker)
        case Left(err) =>
          errors += err
          None
    }

    val registryResult = CheckerRegistry.build(checkers)
    val registry = registryResult match
      case Right(r) => r
      case Left(err) =>
        errors += ValidationError("checkers", s"Duplicate checkers: ${err.properties.map(_.value).mkString(", ")}")
        return Left(errors.result())

    val evaluable: Set[EvaluableConstraint] = constraints.flatMap { c =>
      configFor.get(c) match
        case Some(cc) =>
          cc.evaluability.toLowerCase match
            case "procedural" => Some(ProceduralConstraint(c, cc.version): EvaluableConstraint)
            case _ => Some(MechanicalConstraint(c, cc.version): EvaluableConstraint)
        case None =>
          // Unreachable: every constraint here came from an entry in `parsed`. Reported
          // rather than thrown, because the previous `.get` would have taken the process
          // down if that ever stopped being true.
          errors += ValidationError(
            "constraints", s"no config entry for ${c.property.value} at ${c.level}"
          )
          None
    }.toSet

    val ehResult = EvaluableHierarchy.build(hierarchy, evaluable, registry)
    val eh = ehResult match
      case Right(e) => e
      case Left(errs) =>
        errs.foreach(e => errors += ValidationError("evaluableHierarchy", e.toString))
        return Left(errors.result())

    val engineConfig = EngineConfig(eh, EvidenceSet.empty, Level.Project)
    GovernanceEngine.build(engineConfig) match
      case Right(engine) =>
        val errs = errors.result()
        if errs.nonEmpty then Left(errs) else Right(engine)
      case Left(errs) =>
        errs.foreach(e => errors += ValidationError("engine", e.toString))
        Left(errors.result())

  private def parseLevel(s: String): Level = s.toLowerCase match
    case "enterprise" => Level.Enterprise
    case "department" => Level.Department
    case "project" => Level.Project
    case other => throw IllegalArgumentException(s"Unknown level: $other")

  private def parseLevelOpt(s: String): Option[Level] = s.toLowerCase match
    case "enterprise" => Some(Level.Enterprise)
    case "department" => Some(Level.Department)
    case "project" => Some(Level.Project)
    case _ => None

  private def buildChecker(cb: CheckerBinding): Either[ConfigError, PropertyChecker] =
    val prop = PropertyId.unsafe(cb.property)
    cb.checkerType.toLowerCase match
      case "regex" =>
        val patterns = cb.config.getOrElse("patterns", "").split(",").filter(_.nonEmpty).map(_.r).toSeq
        val mode = cb.config.getOrElse("mode", "any").toLowerCase match
          case "all" => MatchMode.All
          case _ => MatchMode.Any
        Right(RegexChecker(prop, patterns, mode))
      case "keyword" =>
        val keywords = cb.config.getOrElse("keywords", "").split(",").filter(_.nonEmpty).toSet
        val threshold = cb.config.getOrElse("threshold", "1").toInt
        Right(KeywordChecker(prop, keywords, threshold))
      case "structural" =>
        val key = cb.config.getOrElse("key", "")
        val value = cb.config.getOrElse("value", "")
        Right(StructuralChecker(prop, MetadataRequirement.KeyEquals(key, value)))
      case other =>
        Left(CheckerNotFound(cb.property, other))
