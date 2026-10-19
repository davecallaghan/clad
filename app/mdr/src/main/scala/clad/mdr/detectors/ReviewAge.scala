package clad.mdr.detectors

import clad.gateway.config.YamlJson
import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.nio.file.Path
import java.time.{Duration, Instant, LocalDate, ZoneOffset}
import scala.util.Try

/** The constraint-set approval (constraints.yaml `approval.approved_at`) and each attestation (attestations.yaml
  * `attested_at`) older than `review_cadence_days`: due for review. Read fresh each cycle from the config directory. */
final class ReviewAge(cfg: MdrConfig, configDir: Path) extends Detector:
  val id = "review_age"

  /** An ISO instant or date. A date that does not parse is a detector failure, never a silently skipped review. */
  private def instant(what: String, s: String): Either[String, Instant] =
    Try(Instant.parse(s)).orElse(Try(LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant)).toEither
      .left.map(_ => s"$what: '$s' is not an ISO date or instant")

  private def all[A](xs: Vector[Either[String, A]]): Either[String, Vector[A]] =
    xs.foldLeft[Either[String, Vector[A]]](Right(Vector.empty))((acc, x) => acc.flatMap(v => x.map(v :+ _)))

  private def read(name: String): Either[String, ujson.Value] =
    Try(YamlJson.read(configDir.resolve(name))).toEither.left.map(e => s"$name: ${e.getMessage}")

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    val cutoff = now.minus(Duration.ofDays(cfg.reviewCadenceDays))
    for
      constraints <- read("constraints.yaml")
      attestations <- read("attestations.yaml")
      approval <- all(constraints.objOpt.flatMap(_.get("approval")).flatMap(_.objOpt)
        .filter(_.get("status").flatMap(_.strOpt).contains("approved")).toVector.flatMap { a =>
          a.get("approved_at").flatMap(_.strOpt).toVector.map(t => instant("approval.approved_at", t)
            .map(at => ("constraint set", a.get("constraints_digest").flatMap(_.strOpt).getOrElse(""), at)))
        })
      attested <- all(attestations.objOpt.flatMap(_.get("attestations")).flatMap(_.arrOpt).toVector.flatten.flatMap(_.objOpt)
        .flatMap { o =>
          (o.get("template_id").flatMap(_.strOpt), o.get("attested_at").flatMap(_.strOpt)) match
            case (Some(template), Some(t)) =>
              val version = o.get("template_version").map(v => v.strOpt.getOrElse(v.render())).getOrElse("")
              Vector(instant(s"attestation of $template", t).map(at => (template, version, at)))
            case _ => Vector.empty
        })
    yield
      (approval ++ attested).filter(_._3.isBefore(cutoff)).flatMap { (item, version, at) =>
        val key = s"review:$item:$version"
        Option.when(!s.alertedKeys.contains(key))(MdrAlert(key, AlertSeverity.P4Low, AlertCategory.Compliance, id, Vector.empty,
          s"Review due: $item (version $version) was last reviewed $at, more than ${cfg.reviewCadenceDays} days ago.", None))
      }
