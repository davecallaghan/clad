package clad.mdr.detectors

import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.time.Instant

/** Reconciles infra, GIL, records and degraded records, as conformance's LinkageChecks.ghostIds does:
  *  - (a) an infra call whose interaction id has no GIL entry;
  *  - (b) an infra call with no interaction id (listed as `infra@<ts>`);
  *  - (c) a GIL entry older than the in-flight grace with no audit record;
  *  - (d) a GIL entry older than the grace with a 2xx infra call but no `roc` record and no `roc` degraded record.
  * (a) and (b) are P1 bypass. (c) and (d) are P2 when the interaction has a degraded record (a component failed and
  * said so), else P1. One alert per cycle and kind, listing every id; each id is alerted once (`ghost:<id>`). */
final class Ghosts(cfg: MdrConfig) extends Detector:
  val id = "ghosts"

  private def fresh(s: Snapshot)(ids: Vector[String]): Vector[String] =
    ids.distinct.filterNot(i => s.alertedKeys.contains(Source.GhostKeyPrefix + i))

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    val notYet = fresh(s) _
    val cutoff = now.minusSeconds(cfg.inFlightGraceSeconds)
    val audited = s.records.map(_.interactionId).toSet
    val roc = s.records.filter(_.component == "roc").map(_.interactionId).toSet ++
      s.degraded.filter(_.record.component.value == "roc").map(_.record.interactionId.value)
    val degraded = s.degraded.map(_.record.interactionId.value).toSet
    val delivered = s.infra.filter(c => c.status / 100 == 2).flatMap(_.interactionId).toSet
    val projectOf = s.gil.map(g => g.interactionId -> g.project).toMap
    val settled = s.gil.filter(g => !g.registeredAt.isAfter(cutoff))

    val unregistered = notYet(s.infra.flatMap(_.interactionId).filterNot(s.gilIds.contains))
    val noId = s.infra.filter(_.interactionId.isEmpty)
    val noIdFresh = notYet(noId.map(c => s"infra@${c.ts}"))
    val (unrecordedDegraded, unrecorded) = notYet(settled.map(_.interactionId).filterNot(audited.contains)).partition(degraded.contains)
    val (noRocDegraded, noRoc) = notYet(settled.map(_.interactionId)
      .filter(i => audited.contains(i) && delivered.contains(i) && !roc.contains(i))).partition(degraded.contains)

    def alert(kind: String, sev: AlertSeverity, ids: Vector[String], what: String): Option[MdrAlert] =
      Option.when(ids.nonEmpty) {
        val projects = ids.flatMap(projectOf.get).distinct.sorted
        val contain = if projects.isEmpty then "Investigate the caller; no governed project is bound to these calls."
          else s"Recommend containment of ${projects.mkString(", ")} (POST /admin/contain) until explained."
        // each id is alerted once, so the smallest id names this alert uniquely
        MdrAlert(s"$id:$kind:${ids.min}",
          sev, AlertCategory.Governance, id, ids, s"${ids.size} $what. $contain", Option.when(projects.size == 1)(projects.mkString))
      }
    val noIdDetail = noId.filter(c => noIdFresh.contains(s"infra@${c.ts}")).map(c => s"${c.ts} ${c.host}").mkString("; ")
    Right(Vector(
      alert("unregistered", AlertSeverity.P1Critical, unregistered,
        "bypass: model calls outside the governed path (an interaction id with no GIL entry)"),
      alert("no_id", AlertSeverity.P1Critical, noIdFresh,
        s"bypass: model calls outside the governed path (no interaction id): $noIdDetail"),
      alert("unrecorded", AlertSeverity.P1Critical, unrecorded,
        "registered interactions past the in-flight grace with no audit record and no degraded record"),
      alert("unrecorded_degraded", AlertSeverity.P2High, unrecordedDegraded,
        "registered interactions past the in-flight grace with no audit record; a component failed and recorded it"),
      alert("no_roc", AlertSeverity.P1Critical, noRoc, "delivered model outputs with no ROC record"),
      alert("no_roc_degraded", AlertSeverity.P2High, noRocDegraded,
        "delivered model outputs with no ROC record; a component failed and recorded it")
    ).flatten)
