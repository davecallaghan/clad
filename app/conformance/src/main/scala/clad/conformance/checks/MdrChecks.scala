package clad.conformance.checks

import clad.conformance.*
import java.time.{Duration, Instant}

object MdrChecks:
  private val Area = "mdr"

  def all(b: Bundle, mdr: Option[MdrConfig]): Vector[RequirementResult] = Vector(alerted(b, mdr), live(b, mdr))

  private def alerted(b: Bundle, mdr: Option[MdrConfig]): RequirementResult =
    val t = "Ghosts alerted"
    b.alerts match
      case None => RequirementResult("R-MDR-ALERT", t, Area, Verdict.NotRun, reason = "mdr_alerts.jsonl absent")
      case Some(alerts) =>
        val everywhere = (_: Instant) => true
        val alertedIds = alerts.flatMap(_.interactionIds).toSet
        val ghostAlerts = alerts.filter(_.detector == "ghosts")
        val nullCalls = InfraReconciler.unmatched(b, everywhere).filter(_.interactionId.isEmpty)
        val idGhosts = LinkageChecks.ghostIds(b, everywhere).filterNot(_.startsWith("infra@"))
        val missedIds = idGhosts.filterNot(alertedIds.contains)
        val missedNull = nullCalls.filterNot { e =>
          ghostAlerts.exists(a => !a.raisedAt.isBefore(e.ts) &&
            mdr.forall(m => !a.raisedAt.isAfter(e.ts.plusSeconds(m.detectionDelaySeconds))))
        }.map(e => s"infra@${e.ts}")
        val missed = missedIds ++ missedNull
        if missed.isEmpty then RequirementResult("R-MDR-ALERT", t, Area, Verdict.Satisfied, Vector(s"${idGhosts.size + nullCalls.size} ghosts, all alerted"))
        else RequirementResult("R-MDR-ALERT", t, Area, Verdict.Violated, missed, s"${missed.size} ghosts without an alert")

  private def live(b: Bundle, mdr: Option[MdrConfig]): RequirementResult =
    val t = "Monitoring continuity"
    (b.heartbeats, mdr) match
      case (None, _) => RequirementResult("R-MDR-LIVE", t, Area, Verdict.NotRun, reason = "mdr_heartbeats.jsonl absent")
      case (_, None) => RequirementResult("R-MDR-LIVE", t, Area, Verdict.NotRun, reason = "needs mdr.heartbeatIntervalSeconds in the probe config")
      case (Some(beats), Some(m)) =>
        val w = b.meta.window
        val times = (Vector(w.start) ++ beats.map(_.ts).filter(w.contains).sorted ++ Vector(w.end)).distinct
        val allowed = Duration.ofSeconds(m.heartbeatIntervalSeconds * 2)
        val gaps = times.zip(times.drop(1)).filter((a, c) => Duration.between(a, c).compareTo(allowed) > 0)
          .map((a, c) => s"ungoverned monitoring window $a – $c")
        if gaps.isEmpty then RequirementResult("R-MDR-LIVE", t, Area, Verdict.Satisfied, Vector(s"${beats.size} heartbeats, no gap over $allowed"))
        else RequirementResult("R-MDR-LIVE", t, Area, Verdict.Violated, gaps, s"${gaps.size} ungoverned monitoring windows")
