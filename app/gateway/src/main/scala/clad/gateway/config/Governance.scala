package clad.gateway.config

import clad.config.*
import clad.gateway.roc.{OutputConstraintDef, RocConfig}
import java.nio.file.Path

final case class AgentDef(id: String, name: String, kind: String, domains: Vector[String], levels: Vector[String])
final case class ConstraintDef(property: String, ob: Boolean, level: String, domain: String, version: String,
                               evaluability: String, author: String, approver: String)
final case class CheckerDef(property: String, kind: String, patterns: Vector[String], keywords: Vector[String],
                            threshold: Int, key: String, value: String)
final case class ProjectDef(name: String, department: String, tier: String, expectedComponents: Vector[String])

/** The adopter's constraints.yaml (spec section 5): hierarchy, authorship, checkers and the approval block. */
final class Governance private (
  val raw: ujson.Obj, val name: String, val version: String, val agents: Vector[AgentDef],
  val centralApprovers: Vector[String], val criticalDomains: Vector[String], val checkers: Map[String, CheckerDef],
  val enterprise: Vector[ConstraintDef], val departments: Map[String, Vector[ConstraintDef]],
  val projectConstraints: Map[String, Vector[ConstraintDef]], val projects: Map[String, ProjectDef]
):
  def humans: Set[String] = agents.filter(_.kind == "human").map(_.id).toSet

  def path(project: String): Vector[ConstraintDef] =
    enterprise ++ departments(projects(project).department) ++ projectConstraints(project)

  def departmentNames: Set[String] = departments.keySet

  /** The approval covers the constraints and the ROC configuration (thresholds and output constraints are governed too). */
  def approvalDigest(roc: RocConfig): String =
    Canonical.sha256(Canonical.render(ujson.Obj(
      "constraints" -> ujson.Obj.from(raw.value.toSeq.filterNot(_._1 == "approval")),
      "roc" -> roc.raw)))

  private def approval: Map[String, ujson.Value] = raw.value.get("approval") match
    case Some(o: ujson.Obj) => o.value.toMap
    case _                  => Map.empty

  private def str(m: Map[String, ujson.Value], k: String): Option[String] = m.get(k).collect { case ujson.Str(s) => s }

  def approvalPending(roc: RocConfig): Option[String] =
    val a = approval
    if !str(a, "status").contains("approved") then Some(s"approval status is ${str(a, "status").getOrElse("absent")}")
    else if !str(a, "constraints_digest").contains(approvalDigest(roc)) then
      Some("approval constraints_digest does not match the current constraints")
    else if str(a, "approved_at").isEmpty then Some("approval has no approved_at")
    else None

  private def exportedApprover(c: ConstraintDef, roc: RocConfig): String =
    if approvalPending(roc).isEmpty && str(approval, "approved_by").contains(c.approver) then c.approver else ""

  private def cc(roc: RocConfig)(c: ConstraintDef): ConstraintConfig =
    ConstraintConfig(c.property, if c.ob then "obligation" else "prohibition", c.level, c.domain, c.version,
      c.evaluability, c.author, exportedApprover(c, roc))

  private def outputConstraint(tier: String)(c: OutputConstraintDef): OutputConstraintConfig =
    OutputConstraintConfig(c.property, if c.ob then "obligation" else "prohibition", tier,
      Map("kind" -> c.kind) ++ c.category.map("category" -> _))

  /** The project's output constraints and the thresholds that apply to it, plus tau_max (the cap on Critical τ). */
  private def outputConfig(project: String, tier: String, roc: RocConfig): (Seq[OutputConstraintConfig], Seq[ThresholdConfig]) =
    val applicable = roc.forProject(project)
    val properties = applicable.map(_.property).toSet
    val thresholds = roc.thresholds.filter(th => properties.contains(th.property) && roc.thresholdAppliesTo(th, project, this))
      .map(th => ThresholdConfig(th.property, th.tau, tier, th.owner, th.version, th.level))
    (applicable.map(outputConstraint(tier)),
     thresholds :+ ThresholdConfig("tau_max", roc.tauMax, "Critical", roc.owner, roc.version, "enterprise"))

  private def binding(ch: CheckerDef): CheckerBinding = ch.kind match
    case "regex"   => CheckerBinding(ch.property, "regex", Map("patterns" -> ch.patterns.mkString(","), "mode" -> "any"))
    case "keyword" => CheckerBinding(ch.property, "keyword", Map("keywords" -> ch.keywords.mkString(","),
                                                               "threshold" -> ch.threshold.toString))
    case _         => CheckerBinding(ch.property, "structural", Map("key" -> ch.key, "value" -> ch.value))

  def governanceConfig(project: String, roc: RocConfig): GovernanceConfig =
    val p = projects(project)
    val mechanical = path(project).filter(_.evaluability == "mechanical").map(_.property).distinct.sorted
    val posture = Governance.Posture(p.tier)
    val (outputs, thresholds) = outputConfig(project, p.tier, roc)
    GovernanceConfig(
      name = name, version = version, project = project,
      agents = agents.map(a => AgentConfig(a.id, a.name, a.domains, a.levels, a.kind)),
      constraints = ConstraintHierarchyConfig(enterprise.map(cc(roc)), departments(p.department).map(cc(roc)),
                                              projectConstraints(project).map(cc(roc))),
      checkers = mechanical.map(m => binding(checkers(m))), outputConstraints = outputs, thresholds = thresholds,
      failurePosture = FailurePostureConfig(posture, posture, "fail-closed"),
      centralApprovers = centralApprovers, criticalDomains = criticalDomains)

  def configJson(project: String, roc: RocConfig): String = ConfigWriter.toPrettyJson(governanceConfig(project, roc))
  def configDigest(project: String, roc: RocConfig): String = Canonical.sha256(configJson(project, roc))

  def bundleProjects: ujson.Obj = ujson.Obj.from(projects.toSeq.sortBy(_._1).map((n, p) =>
    n -> ujson.Obj("tier" -> p.tier, "expectedComponents" -> ujson.Arr.from(p.expectedComponents.map(ujson.Str(_))))))

object Governance:
  val Posture: Map[String, String] = Map("Critical" -> "fail-closed", "Standard" -> "fail-open-flagged", "Low" -> "fail-open")

  private val CheckerKinds = Vector("regex", "keyword", "structural")
  private val ConstraintTypes = Vector("obligation", "prohibition")
  private val Evaluabilities = Vector("mechanical", "procedural")

  def load(path: Path): Either[String, Governance] =
    try fromJson(YamlJson.read(path))
    catch
      case e: java.io.IOException                    => Left(s"$path: ${e.getMessage}")
      case e: org.yaml.snakeyaml.error.YAMLException => Left(s"$path: ${e.getMessage}")

  private def strs(v: ujson.Value): Vector[String] = v.arr.map(_.str).toVector

  private def constraint(v: ujson.Value, level: String): ConstraintDef =
    val o = v.obj
    def s(k: String): String = o(k) match { case ujson.Str(x) => x; case other => other.render() }
    ConstraintDef(s("property"), s("type") == "obligation", level, s("domain"), s("version"), s("evaluability"),
      s("author"), s("approver"))

  def fromJson(raw: ujson.Value): Either[String, Governance] =
    try
      val o = raw.obj
      val agents = o("agents").arr.map { a =>
        AgentDef(a("id").str, a("name").str, a("kind").str, strs(a("domains")), strs(a("levels"))) }.toVector
      val checkers = o("checkers").obj.map { (p, c) =>
        val m = c.obj
        p -> CheckerDef(p, m("type").str, m.get("patterns").map(strs).getOrElse(Vector.empty),
          m.get("keywords").map(strs).getOrElse(Vector.empty), m.get("threshold").map(_.num.toInt).getOrElse(1),
          m.get("key").map(_.str).getOrElse(""), m.get("value").map(_.str).getOrElse(""))
      }.toMap
      val departments = o("departments").obj.map((d, v) => d -> v("constraints").arr.map(constraint(_, "department")).toVector).toMap
      val deptOf = o("departments").obj.toSeq.flatMap((d, v) => strs(v("projects")).map(_ -> d))
      val projects = o("projects").obj.map { (p, v) =>
        p -> ProjectDef(p, deptOf.find(_._1 == p).map(_._2).getOrElse(""), v("tier").str, strs(v("expected_components")))
      }.toMap
      val projectConstraints = o("projects").obj.map((p, v) => p -> v("constraints").arr.map(constraint(_, "project")).toVector).toMap
      val g = Governance(o, o.get("name").map(_.str).getOrElse("clad-gateway"), o("version") match {
          case ujson.Str(s) => s; case other => other.render() },
        agents, strs(o("central_approvers")), strs(o("critical_domains")), checkers,
        o("enterprise").arr.map(constraint(_, "enterprise")).toVector, departments, projectConstraints, projects)
      validate(g, deptOf.map(_._1)).toLeft(g)
    catch
      case e: NoSuchElementException => Left(s"constraints.yaml: missing field ${e.getMessage}")
      case e: ujson.Value.InvalidData => Left(s"constraints.yaml: ${e.getMessage}")

  private def validate(g: Governance, listedProjects: Seq[String]): Option[String] =
    val all = g.enterprise ++ g.departments.values.flatten ++ g.projectConstraints.values.flatten
    val agent = g.agents.map(a => a.id -> a).toMap
    val tiers = Posture.keySet
    val rawConstraints: Seq[ujson.Value] =
      g.raw("enterprise").arr.toSeq ++ g.raw("departments").obj.values.toSeq.flatMap(_("constraints").arr) ++
        g.raw("projects").obj.values.toSeq.flatMap(_("constraints").arr)
    val enumerations = Iterator(
      g.projects.values.collect { case p if !tiers.contains(p.tier) =>
        s"project ${p.name}: tier ${p.tier} must be one of ${tiers.toSeq.sorted.mkString(", ")}" },
      g.checkers.values.collect { case ch if !CheckerKinds.contains(ch.kind) =>
        s"checker ${ch.property}: type ${ch.kind} must be one of ${CheckerKinds.mkString(", ")}" },
      rawConstraints.flatMap { c =>
        val t = c.obj.get("type").collect { case ujson.Str(x) => x }.getOrElse("absent")
        Option.when(!ConstraintTypes.contains(t))(
          s"constraint ${c.obj.get("property").map(_.str).getOrElse("?")}: type $t must be one of ${ConstraintTypes.mkString(", ")}") },
      all.filterNot(c => Evaluabilities.contains(c.evaluability)).map(c =>
        s"${c.property}: evaluability ${c.evaluability} must be one of ${Evaluabilities.mkString(", ")}")
    ).flatten
    val perConstraint = all.iterator.flatMap { c =>
      Iterator(
        Option.when(c.evaluability == "mechanical" && !g.checkers.contains(c.property))(s"${c.property}: mechanical constraint has no checker"),
        Option.when(c.author == c.approver)(s"${c.property}: author and approver must differ (dual control)"),
        Option.when(!agent.get(c.approver).exists(_.kind == "human"))(s"${c.property}: approver ${c.approver} must be a declared human"),
        Option.when(!agent.get(c.author).exists(a => a.domains.contains(c.domain) && a.levels.contains(c.level)))(
          s"${c.property}: author ${c.author} is not authorised for ${c.domain} at ${c.level}"),
        Option.when(g.criticalDomains.contains(c.domain) && !g.centralApprovers.contains(c.approver))(
          s"${c.property}: ${c.domain} is critical, so the approver must be a central approver")
      ).flatten
    }
    val commas = g.checkers.values.iterator.collect {
      case ch if (ch.patterns ++ ch.keywords).exists(_.contains(",")) =>
        s"${ch.property}: checker patterns and keywords must not contain ',' (clad splits on it)" }
    val membership = g.projects.keys.iterator.collect {
      case p if listedProjects.count(_ == p) != 1 => s"project $p must be in exactly one department" }
    val contradictions = g.projects.keys.iterator.flatMap { p =>
      g.path(p).groupBy(_.property).collect { case (prop, cs) if cs.map(_.ob).distinct.size > 1 =>
        s"$p: contradiction, $prop is both obligatory and prohibited" } }
    val a = g.raw.value.get("approval").collect { case o: ujson.Obj => o.value.toMap }.getOrElse(Map.empty[String, ujson.Value])
    val approvedBy = Option.when(a.get("status").contains(ujson.Str("approved"))) {
      val by = a.get("approved_by").collect { case ujson.Str(s) => s }.getOrElse("")
      Option.when(!(agent.get(by).exists(_.kind == "human") && g.centralApprovers.contains(by)))(
        s"approval approved_by $by must be a human central approver") }.flatten
    (enumerations ++ perConstraint ++ commas ++ membership ++ contradictions ++ approvedBy.iterator).nextOption()
