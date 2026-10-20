package clad.mdr.detectors

import clad.mdr.*
import clad.monitoring.{AlertCategory, AlertSeverity}
import java.time.{Duration, Instant}

/** Per constraint property: the share of its evaluations that blocked (an unsatisfied entry in a record whose outcome
  * is a block or a fallback) in the window, against the same share over the trailing `block_rate_baseline_hours`
  * before it. Alerts when the window's rate exceeds `block_rate_multiple` times the baseline. A property with no
  * baseline evaluations has no rate to compare and is not alerted. */
final class BlockRate(cfg: MdrConfig) extends Detector:
  val id = "block_rate"

  /** The lookback a snapshot needs for this detector's baseline. */
  def lookback: Duration = Duration.ofHours(cfg.blockRateBaselineHours)

  private def blocking(outcome: Option[String]): Boolean =
    outcome.exists(o => o == "block" || o.startsWith("block:") || o.startsWith("fallback:"))

  private def rates(rs: Vector[RecordRow]): Map[String, (Int, Int)] =
    rs.flatMap(r => r.entries.map(e => e.constraint.property.value -> (!e.satisfied && blocking(r.outcome))))
      .groupBy(_._1).view.mapValues(v => (v.count(_._2), v.size)).toMap

  def run(s: Snapshot, now: Instant): Either[String, Vector[MdrAlert]] =
    val baselineStart = s.window.start.minus(lookback)
    val inWindow = s.records.filter(r => s.window.contains(r.timestamp))
    val window = rates(inWindow)
    // keyed by the property's newest blocking record, not the window end: windows overlap by the in-flight grace, so
    // the same records must not alert again next cycle
    def newestBlock(property: String): Instant =
      inWindow.filter(r => blocking(r.outcome) && r.entries.exists(e => e.constraint.property.value == property && !e.satisfied))
        .map(_.timestamp).maxOption.getOrElse(s.window.end)
    val baseline = rates(s.records.filter(r => !r.timestamp.isBefore(baselineStart) && r.timestamp.isBefore(s.window.start)))
    Right(window.toVector.sortBy(_._1).flatMap { case (property, (blocked, evaluated)) =>
      baseline.get(property).filter(_._2 > 0).flatMap { (bBlocked, bEvaluated) =>
        val rate = blocked.toDouble / evaluated
        val base = bBlocked.toDouble / bEvaluated
        val key = s"block_rate:$property:${newestBlock(property)}"
        Option.when(rate > cfg.blockRateMultiple * base && !s.alertedKeys.contains(key))(
          MdrAlert(key, AlertSeverity.P3Medium, AlertCategory.Governance, id, Vector.empty,
            f"Block rate of $property is $rate%.3f ($blocked of $evaluated) in ${s.window.start} – ${s.window.end}, against " +
              f"$base%.3f ($bBlocked of $bEvaluated) over the previous ${cfg.blockRateBaselineHours} h (alert above " +
              s"${cfg.blockRateMultiple} times). Check for an attack, a template change or a provider change.", None))
      }
    })
