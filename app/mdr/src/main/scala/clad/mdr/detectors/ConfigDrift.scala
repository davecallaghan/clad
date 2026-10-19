package clad.mdr.detectors

import clad.gateway.config.Models
import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.time.Instant
import scala.util.Try

/** Per GIL entry in the window: a model not `approved` in models.yaml; temperature or max_tokens beyond
  * `config_bounds`; a template using a model it has not used earlier in the snapshot (a model version change; the
  * models one template alternates between by tier are each alerted once, when first seen). Per record: a
  * configDigest that is not in config_version. */
final class ConfigDrift(cfg: MdrConfig, models: () => Either[String, Models]) extends Detector:
  val id = "config"

  private def num(g: GilRow, k: String): Option[Double] =
    g.metadata.value.get(k).flatMap(_.strOpt).flatMap(v => Try(v.toDouble).toOption)

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] = models().map { m =>
    def alert(key: String, ids: Vector[String], text: String, project: Option[String]) =
      Option.when(!s.alertedKeys.contains(key))(MdrAlert(key, AlertSeverity.P2High, AlertCategory.Compliance, id, ids, text, project))
    val inWindow = s.gil.filter(g => s.window.contains(g.registeredAt))
    val perEntry = inWindow.flatMap { g =>
      val model = g.metadata.value.get("model").flatMap(_.strOpt)
      Vector(
        model.filter(md => m.statusOf(md) != "approved").flatMap(md => alert(s"config:${g.interactionId}:unapproved_model",
          Vector(g.interactionId), s"Model $md is ${m.statusOf(md)}, not approved in models.yaml.", Some(g.project))),
        num(g, "temperature").filter(_ > cfg.temperatureMax).flatMap(t => alert(s"config:${g.interactionId}:temperature",
          Vector(g.interactionId), s"Temperature $t is above the bound ${cfg.temperatureMax}.", Some(g.project))),
        num(g, "max_tokens").filter(_ > cfg.maxTokensMax).flatMap(t => alert(s"config:${g.interactionId}:max_tokens",
          Vector(g.interactionId), s"max_tokens $t is above the bound ${cfg.maxTokensMax}.", Some(g.project)))
      ).flatten
    }
    val changes = s.gil.groupBy(_.metadata.value.get("template_id").flatMap(_.strOpt)).toVector.flatMap {
      case (Some(template), rows) =>
        val sorted = rows.sortBy(_.registeredAt)
        sorted.zipWithIndex.drop(1).flatMap { (g, i) =>
          val model = g.metadata.value.get("model").flatMap(_.strOpt)
          val seen = sorted.take(i).flatMap(_.metadata.value.get("model").flatMap(_.strOpt)).toSet
          model.filter(md => !seen.contains(md) && s.window.contains(g.registeredAt)).flatMap(md =>
            alert(s"config:$template:$md:model_changed", Vector(g.interactionId),
              s"Template $template now uses model $md (earlier: ${seen.toVector.sorted.mkString(", ")}).", Some(g.project)))
        }.distinctBy(_.dedupeKey)
      case _ => Vector.empty
    }
    val unknown = s.records.filter(r => s.window.contains(r.timestamp) && !s.configDigests.contains(r.configDigest))
      .flatMap(r => alert(s"config:${r.interactionId}:unknown_config", Vector(r.interactionId),
        s"Record (${r.component}) names config ${r.configDigest}, which is not in config_version.", None))
    perEntry ++ changes ++ unknown.distinctBy(_.dedupeKey)
  }
