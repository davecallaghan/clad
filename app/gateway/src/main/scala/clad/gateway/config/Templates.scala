package clad.gateway.config

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.util.matching.Regex

final case class Template(id: String, version: String, project: String, tier: String, model: String, modelTier: String,
                          text: String, responseFormat: String, evidenceFormat: String, slots: Map[String, String])

final case class Models(provider: String, tiers: Map[String, String], statuses: Map[String, String]):
  def statusOf(id: String): String = statuses.getOrElse(id, "unapproved")

final class Templates(val byId: Map[String, Template], val models: Models):
  def get(id: String): Option[Template] = byId.get(id)
  def all: Vector[Template] = byId.values.toVector.sortBy(_.id)
  def modelFor(tier: String): Option[String] = models.tiers.get(tier)
  def metadata(t: Template, model: String): Map[String, String] =
    Map("template_id" -> t.id, "template_version" -> t.version, "project" -> t.project, "provider" -> models.provider,
        "model" -> model, "model_registry_status" -> models.statusOf(model), "response_format" -> t.responseFormat)

object Templates:
  private val Slot = """\{\{(\w+)\}\}""".r

  /** One pass over the template text: slot values are inserted literally and never re-expanded. */
  def render(t: Template, vars: Map[String, String]): Either[String, String] =
    val missing = t.slots.keySet -- vars.keySet
    val extra = vars.keySet -- t.slots.keySet
    if missing.nonEmpty then Left(s"${t.id}: missing slot values ${missing.toVector.sorted.mkString(", ")}")
    else if extra.nonEmpty then Left(s"${t.id}: undeclared slots ${extra.toVector.sorted.mkString(", ")}")
    else Right(Slot.replaceAllIn(t.text, m => Regex.quoteReplacement(vars(m.group(1)))))

  def loadModels(path: Path): Either[String, Models] =
    Loading.guard(path) {
      try
        val o = YamlJson.read(path).obj
        val rows = Seq("approved", "classifier").flatMap(k => o.get(k).map(_.arr.toSeq).getOrElse(Seq.empty))
        Right(Models(o("provider").str, o("tiers").obj.map((k, v) => k -> v.str).toMap,
          rows.map(r => r("id").str -> r("status").str).toMap))
      catch case e: NoSuchElementException => Left(s"$path: missing field ${e.getMessage}")
    }

  def load(dir: Path, models: Models, gov: Governance): Either[String, Templates] =
    Loading.guard(dir) {
      val files =
        val s = Files.list(dir)
        try s.iterator().asScala.filter(_.toString.endsWith(".yaml")).toVector.sortBy(_.toString)
        finally s.close()
      files.foldLeft[Either[String, Map[String, Template]]](Right(Map.empty)) { (acc, f) =>
        acc.flatMap { m =>
          Loading.guard(f) {
            try
              val o = YamlJson.read(f).obj
              val id = o("id").str
              val file = f.getFileName.toString.stripSuffix(".yaml")
              if id != file then Left(s"$file.yaml: id $id does not match the file name")
              else
                val project = o("project").str
                for
                  p <- gov.projects.get(project).toRight(s"$id: unknown project $project")
                  model <- models.tiers.get(o("model_tier").str).toRight(s"$id: unknown model tier ${o("model_tier").str}")
                yield m + (id -> Template(id, o("version") match { case ujson.Str(s) => s; case v => v.render() },
                  project, p.tier, model, o("model_tier").str, o("text").str.stripTrailing, o("response_format").str,
                  o("evidence_format").str, o.get("slots").collect { case s: ujson.Obj => s.value.map((k, v) => k -> v.str).toMap }.getOrElse(Map.empty)))
            catch case e: NoSuchElementException => Left(s"${f.getFileName}: missing field ${e.getMessage}")
          }
        }
      }.map(Templates(_, models))
    }
