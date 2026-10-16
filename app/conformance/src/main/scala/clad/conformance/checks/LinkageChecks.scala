package clad.conformance.checks

import clad.conformance.*
import clad.integrity.{GhostDetector, InteractionId}
import java.time.Instant

object LinkageChecks:
  private val Area = "linkage"

  /** Ids (or "infra@<ts>" for calls with no id) of every ghost in the given window. */
  def ghostIds(b: Bundle, inWindow: Instant => Boolean): Vector[String] =
    val audited = b.audit.getOrElse(Vector.empty).flatMap(_.record.interactionId).toSet
    val degraded = b.degraded.getOrElse(Vector.empty).map(_.record.interactionId).toSet
    val gil = b.gil.getOrElse(Vector.empty).filter(g => inWindow(g.registeredAt)).sortBy(_.registeredAt)
    val fromGil = GhostDetector.detect(gil, audited, degraded).ghosts.map(_.interactionId.value)
    val fromInfra = InfraReconciler.unmatched(b, inWindow).map(e => e.interactionId.getOrElse(s"infra@${e.ts}")).distinct
    (fromGil ++ fromInfra).distinct

  def all(b: Bundle): Vector[RequirementResult] = Vector(ea1(b), ea2(b), ea3(b))

  private def ea1(b: Bundle): RequirementResult =
    val t = "Single chokepoint"
    b.infra match
      case None => RequirementResult("R-EA1", t, Area, Verdict.NotRun, reason = "infra.jsonl absent")
      case Some(_) =>
        val un = InfraReconciler.unmatched(b, b.inWorkload)
        if un.isEmpty then RequirementResult("R-EA1", t, Area, Verdict.Satisfied, Vector("every model call carries a registered interaction id"))
        else RequirementResult("R-EA1", t, Area, Verdict.Violated,
          un.map(e => s"${e.ts} ${e.host} ${e.path} id=${e.interactionId.getOrElse("null")}"),
          s"${un.size} model calls reached a provider without a GIL entry")

  private def ea2(b: Bundle): RequirementResult =
    val t = "Identity binding"
    b.gil match
      case None => RequirementResult("R-EA2", t, Area, Verdict.NotRun, reason = "gil.jsonl absent")
      case Some(gil) =>
        val bad = gil.filterNot(g =>
          g.metadata.get("project").exists(p => p.nonEmpty && b.meta.projects.contains(p)) &&
          g.metadata.get("principal").exists(_.nonEmpty))
        if bad.isEmpty then RequirementResult("R-EA2", t, Area, Verdict.Satisfied, Vector(s"${gil.size} entries bound"))
        else RequirementResult("R-EA2", t, Area, Verdict.Violated, bad.map(_.interactionId.value), s"${bad.size} entries lack a known project or a principal")

  private def ea3(b: Bundle): RequirementResult =
    val t = "Bypass detection"
    if b.gil.isEmpty && b.infra.isEmpty then RequirementResult("R-EA3", t, Area, Verdict.NotRun, reason = "needs gil.jsonl or infra.jsonl")
    else
      val ghosts = ghostIds(b, b.inWorkload)
      val gilCount = b.gil.getOrElse(Vector.empty).count(g => b.inWorkload(g.registeredAt))
      val infraOnly = InfraReconciler.unmatched(b, b.inWorkload).map(e => e.interactionId.getOrElse(s"infra@${e.ts}")).distinct.size
      val total = gilCount + infraOnly
      val rate = if total == 0 then 0.0 else ghosts.size.toDouble / total
      val unlinkable = b.audit.getOrElse(Vector.empty).count(_.record.interactionId.isEmpty)
      val metrics = Map("ghostRate" -> rate, "ghostCount" -> ghosts.size.toDouble, "unlinkableRecords" -> unlinkable.toDouble)
      if ghosts.isEmpty then RequirementResult("R-EA3", t, Area, Verdict.Satisfied, Vector(s"$total interactions, 0 ghosts"), metrics = metrics)
      else RequirementResult("R-EA3", t, Area, Verdict.Violated, ghosts, f"${ghosts.size} ghosts of $total interactions (rate $rate%.3f)", metrics)
