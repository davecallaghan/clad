package clad.gateway.roc

import clad.gateway.config.*
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** An output constraint the ROC checks: deterministic (a checker in this gateway) or classifier (a model's probability). */
final case class OutputConstraintDef(property: String, ob: Boolean, kind: String /* deterministic|classifier */,
                                     templates: Set[String], projects: Set[String] /* empty = all */, category: Option[String])

/** τ for one classifier property at one level of the hierarchy. `scope` is "" at enterprise, a department or a project name below. */
final case class ThresholdDef(property: String, level: String /* enterprise|department|project */, scope: String,
                              tau: Double, version: String, owner: String)

/** The measured classifier. `verdictTokens` is how many tokens the classifier call may generate to reach its verdict. */
final case class ClassifierProfile(id: String, model: String, version: String, upstream: String, verdictTokens: Int,
                                   measuredFp: Option[Double], measuredFn: Option[Double], validatedAt: Option[String])

/** The adopter's roc.yaml and classifiers/llama_guard.yaml. `raw` is roc.yaml as read: the approval digest covers it.
  * `templateProjects` maps a template id to its project (read from prompts/), so a constraint that names templates can be
  * exported for the projects those templates belong to. */
final case class RocConfig(constraints: Vector[OutputConstraintDef], thresholds: Vector[ThresholdDef], tauMax: Double,
                           routerIntents: Set[String], safeResponses: Map[String, String], retryVariant: String,
                           classifier: ClassifierProfile, owner: String, version: String,
                           templateProjects: Map[String, String], raw: ujson.Value):

  private def appliesTo(c: OutputConstraintDef, templateId: String, project: String): Boolean =
    (c.templates.isEmpty || c.templates.contains(templateId)) && (c.projects.isEmpty || c.projects.contains(project))

  /** The constraints the ROC evaluates on this template's output. */
  def forTemplate(t: Template): Vector[OutputConstraintDef] = constraints.filter(appliesTo(_, t.id, t.project))

  /** The constraints that apply to any template of the project. */
  def forProject(project: String): Vector[OutputConstraintDef] =
    constraints.filter { c =>
      val ids = templateProjects.collect { case (id, p) if p == project => id }
      if c.templates.isEmpty then c.projects.isEmpty || c.projects.contains(project)
      else ids.exists(appliesTo(c, _, project))
    }

  def thresholdAppliesTo(th: ThresholdDef, project: String, gov: Governance): Boolean = th.level match
    case "enterprise" => true
    case "department" => gov.projects.get(project).exists(_.department == th.scope)
    case _            => th.scope == project

  /** The lowest applicable level wins; a property with no threshold at all is held to tau_max, the cap every τ sits under. */
  def tau(property: String, project: String, governance: Governance): Double =
    thresholds.filter(th => th.property == property && thresholdAppliesTo(th, project, governance))
      .sortBy(th => -RocConfig.Levels.indexOf(th.level)).headOption.map(_.tau).getOrElse(tauMax)

object RocConfig:
  val Levels: Vector[String] = Vector("enterprise", "department", "project")
  private val Kinds = Vector("deterministic", "classifier")
  private val Types = Vector("obligation", "prohibition")

  def load(dir: Path, gov: Governance): Either[String, RocConfig] =
    val rocPath = dir.resolve("roc.yaml")
    val profilePath = dir.resolve("classifiers").resolve("llama_guard.yaml")
    for
      raw <- Loading.guard(rocPath)(Right(YamlJson.read(rocPath)))
      profile <- Loading.guard(profilePath)(parseProfile(YamlJson.read(profilePath)))
      projects <- Loading.guard(dir.resolve("prompts"))(templateProjects(dir.resolve("prompts")))
      roc <- Loading.guard(rocPath)(parse(raw, profile, projects))
      _ <- validate(roc, gov).toLeft(())
    yield roc

  private def field(o: ujson.Value, file: String, k: String): Either[String, ujson.Value] =
    o.objOpt.flatMap(_.get(k)).filter(_ != ujson.Null).toRight(s"$file: $k is required")

  private def str(o: ujson.Value, file: String, k: String): Either[String, String] =
    field(o, file, k).flatMap {
      case ujson.Str(s) => Right(s)
      case ujson.Num(n) => Right(ujson.Num(n).render())
      case _            => Left(s"$file: $k must be a string")
    }

  private def num(o: ujson.Value, file: String, k: String): Either[String, Double] =
    field(o, file, k).flatMap { case ujson.Num(n) => Right(n); case _ => Left(s"$file: $k must be a number") }

  private def optNum(o: ujson.Value, k: String): Option[Double] = o.objOpt.flatMap(_.get(k)).collect { case ujson.Num(n) => n }
  private def optStr(o: ujson.Value, k: String): Option[String] = o.objOpt.flatMap(_.get(k)).collect { case ujson.Str(s) => s }
  private def strSet(o: ujson.Value, k: String): Set[String] =
    o.objOpt.flatMap(_.get(k)).collect { case a: ujson.Arr => a.value.collect { case ujson.Str(s) => s }.toSet }.getOrElse(Set.empty)

  private def all[A](xs: Seq[Either[String, A]]): Either[String, Vector[A]] =
    xs.foldLeft[Either[String, Vector[A]]](Right(Vector.empty))((acc, x) => acc.flatMap(v => x.map(v :+ _)))

  private def parseProfile(v: ujson.Value): Either[String, ClassifierProfile] =
    val f = "classifiers/llama_guard.yaml"
    for
      id <- str(v, f, "id"); model <- str(v, f, "model"); version <- str(v, f, "version"); upstream <- str(v, f, "upstream")
      tokens <- num(v, f, "verdict_tokens")
    yield ClassifierProfile(id, model, version, upstream, tokens.toInt, optNum(v, "measured_fp"), optNum(v, "measured_fn"),
      optStr(v, "validated_at"))

  private def templateProjects(prompts: Path): Either[String, Map[String, String]] =
    if !Files.isDirectory(prompts) then Right(Map.empty)
    else
      val files = Files.list(prompts).iterator().asScala.filter(_.toString.endsWith(".yaml")).toVector.sortBy(_.toString)
      all(files.map { p =>
        val y = YamlJson.read(p)
        for id <- str(y, p.toString, "id"); project <- str(y, p.toString, "project") yield id -> project
      }).map(_.toMap)

  private def parse(raw: ujson.Value, profile: ClassifierProfile, projects: Map[String, String]): Either[String, RocConfig] =
    val f = "roc.yaml"
    for
      version <- str(raw, f, "version")
      tauMax <- num(raw, f, "tau_max")
      owner <- str(raw, f, "owner")
      retry <- str(raw, f, "retry_variant")
      cs <- field(raw, f, "constraints").flatMap(_.arrOpt.toRight(s"$f: constraints must be a list"))
      ths <- field(raw, f, "thresholds").flatMap(_.arrOpt.toRight(s"$f: thresholds must be a list"))
      intents <- field(raw, f, "router_intents").flatMap(_.arrOpt.toRight(s"$f: router_intents must be a list"))
      safe <- field(raw, f, "safe_responses").flatMap(_.objOpt.toRight(s"$f: safe_responses must be a mapping"))
      constraints <- all(cs.toSeq.map { c =>
        for
          p <- str(c, f, "property"); t <- str(c, f, "type"); k <- str(c, f, "kind")
          _ <- Either.cond(Types.contains(t), (), s"$f: $p: type $t must be one of ${Types.mkString(", ")}")
          _ <- Either.cond(Kinds.contains(k), (), s"$f: $p: kind $k must be one of ${Kinds.mkString(", ")}")
        yield OutputConstraintDef(p, t == "obligation", k, strSet(c, "templates"), strSet(c, "projects"), optStr(c, "category"))
      })
      thresholds <- all(ths.toSeq.map { t =>
        for
          p <- str(t, f, "property"); l <- str(t, f, "level"); tau <- num(t, f, "tau"); v <- str(t, f, "version")
          _ <- Either.cond(Levels.contains(l), (), s"$f: $p: level $l must be one of ${Levels.mkString(", ")}")
        yield ThresholdDef(p, l, optStr(t, "scope").getOrElse(""), tau, v, optStr(t, "owner").getOrElse(owner))
      })
      responses <- all(safe.value.toSeq.map((k, x) => x match
        case ujson.Str(s) => Right(k -> s)
        case _            => Left(s"$f: safe_responses.$k must be a string")))
    yield RocConfig(constraints, thresholds, tauMax, intents.collect { case ujson.Str(s) => s }.toSet, responses.toMap, retry,
      profile, owner, version, projects, raw)

  /** None when valid. A lower level may only lower τ, and a Critical project's τ never exceeds tau_max. */
  private def validate(roc: RocConfig, gov: Governance): Option[String] =
    val scopeErrors = roc.thresholds.flatMap { th =>
      th.level match
        case "enterprise" => Option.when(th.scope.nonEmpty)(s"roc.yaml: ${th.property}: an enterprise threshold has no scope")
        case "department" => Option.when(!gov.departmentNames.contains(th.scope))(s"roc.yaml: ${th.property}: unknown department '${th.scope}'")
        case _            => Option.when(!gov.projects.contains(th.scope))(s"roc.yaml: ${th.property}: unknown project '${th.scope}'")
    }
    val duplicates = roc.thresholds.groupBy(th => (th.property, th.level, th.scope)).collect {
      case (k, v) if v.size > 1 => s"roc.yaml: duplicate threshold for ${k._1} at ${k._2} ${k._3}".trim }
    val monotonic = roc.thresholds.filter(_.level != "enterprise").flatMap { th =>
      val higher = roc.thresholds.filter(h => h.property == th.property && RocConfig.Levels.indexOf(h.level) < RocConfig.Levels.indexOf(th.level) &&
        gov.projects.keys.exists(p => roc.thresholdAppliesTo(th, p, gov) && roc.thresholdAppliesTo(h, p, gov)))
      if !higher.exists(_.level == "enterprise") then Some(s"roc.yaml: ${th.property} has a ${th.level} threshold but no enterprise one")
      else higher.collectFirst { case h if th.tau > h.tau =>
        s"roc.yaml: ${th.property} at ${th.level} ${th.scope} is ${th.tau}, above ${h.level} ${h.tau}: a lower level may only lower τ" }
    }
    val critical = roc.thresholds.flatMap { th =>
      val covered = gov.projects.keys.filter(roc.thresholdAppliesTo(th, _, gov))
      Option.when(th.tau > roc.tauMax && covered.exists(gov.projects(_).tier == "Critical"))(
        s"roc.yaml: ${th.property} at ${th.level} is ${th.tau}, above tau_max ${roc.tauMax} for a Critical project")
    }
    val unthresholded = roc.constraints.filter(_.kind == "classifier").filterNot(c =>
      roc.thresholds.exists(th => th.property == c.property && th.level == "enterprise")).map(c =>
      s"roc.yaml: classifier constraint ${c.property} has no enterprise threshold")
    (scopeErrors ++ duplicates ++ monotonic ++ critical ++ unthresholded).headOption
