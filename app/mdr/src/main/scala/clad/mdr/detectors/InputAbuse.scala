package clad.mdr.detectors

import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.time.Instant

/** Per principal and session: `injection_alert_count` or more injection signals within `injection_window_seconds`.
  * One alert per burst, keyed by the burst's first signal. */
final class InputAbuse(cfg: MdrConfig) extends Detector:
  val id = "input"

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    val who = s.gil.map(g => g.interactionId -> (g.principal, g.metadata.value.get("session").flatMap(_.strOpt).getOrElse(""))).toMap
    val injections = s.signals.filter(_._2.category == "injection").flatMap((iid, _, at) => who.get(iid).map(w => (w, iid, at)))
    Right(injections.groupBy(_._1).toVector.sortBy(_._1).flatMap { case ((principal, session), xs) =>
      val sorted = xs.sortBy(_._3)
      sorted.indices.iterator.map { end =>
        sorted.filter(x => !x._3.isAfter(sorted(end)._3) && !x._3.isBefore(sorted(end)._3.minusSeconds(cfg.injectionWindowSeconds)))
      }.find(_.size >= cfg.injectionAlertCount).flatMap { burst =>
        val key = s"input:$principal:${burst.map(_._3).min}"
        Option.when(!s.alertedKeys.contains(key))(MdrAlert(key, AlertSeverity.P2High, AlertCategory.Adversarial, id,
          burst.map(_._2).distinct, s"${burst.size} prompt-injection signals from principal $principal" +
            (if session.isEmpty then "" else s", session $session") + s" within ${cfg.injectionWindowSeconds} s. " +
            "Review the interactions; consider containment of the project if the principal is not trusted.",
          s.gil.find(_.interactionId == burst.map(_._2).head).map(_.project)))
      }
    })
