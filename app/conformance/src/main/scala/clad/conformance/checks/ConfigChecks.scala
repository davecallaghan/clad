package clad.conformance.checks

import clad.config.*
import clad.conformance.*
import clad.core.*
import clad.evaluation.EvaluableHierarchy
import java.time.Instant

object ConfigChecks:
  private val Area = "config"

  def loaded(b: Bundle): Map[String, Either[String, (GovernanceConfig, EvaluableHierarchy)]] =
    b.configs.map { (digest, f) =>
      digest -> (for
        c <- ConfigLoader.loadFromString(f.text).left.map(_.mkString("; "))
        eh <- ConfigLoader.buildHierarchy(c).left.map(_.mkString("; "))
      yield (c, eh))
    }

  def all(b: Bundle): Vector[RequirementResult] =
    if b.configs.isEmpty then
      Vector("R-CFG-PARSE" -> "Configs load", "R-CFG-MONO" -> "Hierarchy monotonic, no contradictions",
             "R-CFG-TENSION" -> "Tensions surfaced", "R-EA4" -> "Constraint authorship")
        .map((id, t) => RequirementResult(id, t, Area, Verdict.NotRun, reason = "no configs in bundle")) :+ ax5(b)
    else
      val l = loaded(b)
      Vector(parse(l), mono(b, l), tension(b), ea4(b), ax5(b))

  private def parse(l: Map[String, Either[String, (GovernanceConfig, EvaluableHierarchy)]]): RequirementResult =
    val bad = l.collect { case (d, Left(e)) => s"$d: $e" }.toVector
    if bad.isEmpty then RequirementResult("R-CFG-PARSE", "Configs load", Area, Verdict.Satisfied, Vector(s"${l.size} configs"))
    else RequirementResult("R-CFG-PARSE", "Configs load", Area, Verdict.Violated, bad, s"${bad.size} configs fail to load")

  private def enterpriseSet(c: GovernanceConfig): Set[(String, String)] =
    c.constraints.enterprise.map(cc => (cc.property, cc.constraintType.toLowerCase)).toSet

  private def mono(b: Bundle, l: Map[String, Either[String, (GovernanceConfig, EvaluableHierarchy)]]): RequirementResult =
    val t = "Hierarchy monotonic, no contradictions"
    val broken = l.collect { case (d, Left(e)) => s"$d: $e" }.toVector
    val parsed = b.configs.values.flatMap(f => ConfigLoader.loadFromString(f.text).toOption).toVector
    val enterpriseSets = parsed.map(enterpriseSet).distinct
    val problems = broken ++ (if enterpriseSets.size > 1 then Vector("enterprise constraints differ between project configs") else Vector.empty)
    if problems.isEmpty then RequirementResult("R-CFG-MONO", t, Area, Verdict.Satisfied, Vector("no contradictions; enterprise constraints identical on every path"))
    else RequirementResult("R-CFG-MONO", t, Area, Verdict.Violated, problems, problems.mkString("; "))

  private def tension(b: Bundle): RequirementResult =
    val found = b.configs.values.flatMap(f => ConfigLoader.loadFromString(f.text).toOption).flatMap { c =>
      val all = c.constraints.enterprise ++ c.constraints.department ++ c.constraints.project
      val pairs = all.flatMap { cc =>
        val lvl = cc.level.toLowerCase match
          case "enterprise" => Level.Enterprise
          case "department" => Level.Department
          case _ => Level.Project
        val p = PropertyId.unsafe(cc.property)
        val con: Constraint = if cc.constraintType.equalsIgnoreCase("obligation") then Constraint.Obligation(p, lvl) else Constraint.Prohibition(p, lvl)
        Some(con -> Domain(cc.domain))
      }.toMap
      TensionDetector.detectCrossDomainTensions(pairs.keySet, pairs).map(t => s"${c.project}: ${t.advisory}")
    }.toVector.distinct
    RequirementResult("R-CFG-TENSION", "Tensions surfaced", Area, Verdict.Satisfied,
      if found.isEmpty then Vector("no tensions") else found, "advisory")

  private def ea4(b: Bundle): RequirementResult =
    val t = "Constraint authorship"
    val problems = b.configs.values.flatMap(f => ConfigLoader.loadFromString(f.text).toOption).flatMap { c =>
      val agents = c.agents.map(a => a.id -> a).toMap
      val built = ConfigLoader.buildAgents(c).map(a => a.id -> a).toMap
      (c.constraints.enterprise ++ c.constraints.department ++ c.constraints.project).flatMap { cc =>
        val who = s"${c.project}/${cc.property}@${cc.level}"
        val lvl = cc.level.toLowerCase match
          case "enterprise" => Level.Enterprise
          case "department" => Level.Department
          case _ => Level.Project
        val p = PropertyId.unsafe(cc.property)
        val con: Constraint = if cc.constraintType.equalsIgnoreCase("obligation") then Constraint.Obligation(p, lvl) else Constraint.Prohibition(p, lvl)
        Vector(
          Option.when(cc.author.isEmpty || cc.approver.isEmpty)(s"$who: author or approver missing"),
          Option.when(cc.author.nonEmpty && cc.author == cc.approver)(s"$who: author is the approver"),
          Option.when(cc.approver.nonEmpty && !agents.get(cc.approver).exists(_.kind == "human"))(s"$who: approver ${cc.approver} is not a human agent"),
          built.get(cc.author) match
            case None => Option.when(cc.author.nonEmpty)(s"$who: author ${cc.author} is not a declared agent")
            case Some(a) => ConstraintAuthorizer.authorize(AuthorizationContext(a, Instant.EPOCH), con, Domain(cc.domain)).left.toOption.map(e => s"$who: ${e.mkString(", ")}"),
          Option.when(c.criticalDomains.contains(cc.domain) && !c.centralApprovers.contains(cc.approver))(s"$who: critical domain ${cc.domain} needs a central approver")
        ).flatten
      }
    }.toVector.distinct
    if problems.isEmpty then RequirementResult("R-EA4", t, Area, Verdict.Satisfied, Vector("dual control and RBAC hold for every constraint"))
    else RequirementResult("R-EA4", t, Area, Verdict.Violated, problems, s"${problems.size} authorship problems")

  private def ax5(b: Bundle): RequirementResult =
    val t = "Version identity"
    b.audit match
      case None | Some(Vector()) => RequirementResult("R-AX5", t, Area, Verdict.NotRun, reason = "no audit records")
      case Some(rs) =>
        val badConfigFiles = b.configs.values.filter(f => f.computedDigest != f.name).map(f => s"configs/${f.name}.json hashes to ${f.computedDigest}")
        val badArtifacts = b.artifacts.values.filter(a => a.computedDigest != a.name).map(a => s"artifacts/${a.name}.json content hashes to ${a.computedDigest}")
        val unknownConfig = rs.map(_.record.configDigest).distinct.filterNot(b.configs.contains).map(d => s"configDigest $d not in bundle")
        val unknownArtifact = rs.filter(_.record.component.contains("epg")).map(_.record.artifactDigest).distinct
          .filterNot(b.artifacts.contains).map(d => s"EPG artifact $d not in bundle")
        val problems = (badConfigFiles ++ badArtifacts ++ unknownConfig ++ unknownArtifact).toVector
        if problems.isEmpty then RequirementResult("R-AX5", t, Area, Verdict.Satisfied, Vector("every config and EPG artifact resolves and hashes correctly"))
        else RequirementResult("R-AX5", t, Area, Verdict.Violated, problems, s"${problems.size} version identity problems")
