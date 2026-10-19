package clad.mdr.detectors

import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.time.Instant

/** Each ROC record with outcome `flag` (a classifier score at or over tau on a delivered Standard output): the review
  * queue. */
final class Flags extends Detector:
  val id = "flags"

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    val project = s.gil.map(g => g.interactionId -> g.project).toMap
    Right(s.records.filter(r => r.component == "roc" && r.outcome.contains("flag")).map(_.interactionId).distinct
      .filterNot(i => s.alertedKeys.contains(s"flag:$i")).map { i =>
        val flagged = s.records.filter(r => r.interactionId == i && r.outcome.contains("flag")).flatMap(_.entries)
          .filterNot(_.satisfied).map(_.constraint.property.value).distinct
        MdrAlert(s"flag:$i", AlertSeverity.P3Medium, AlertCategory.Compliance, id, Vector(i),
          s"Delivered output flagged for review (${flagged.mkString(", ")}): a classifier score reached its threshold.",
          project.get(i))
      })
