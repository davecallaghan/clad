package clad.conformance.checks

import clad.config.ConfigLoader
import clad.conformance.*
import clad.evaluation.ClassifierDetail
import clad.integrity.*
import clad.runtime.EvaluabilityClass

object RocChecks:
  private val Area = "roc"

  def all(b: Bundle): Vector[RequirementResult] = Vector(present(b), decision(b), thresholds(b), posture(b))

  private def rocIds(b: Bundle): Set[String] =
    b.audit.getOrElse(Vector.empty).filter(_.record.component.contains("roc")).flatMap(_.record.interactionId.map(_.value)).toSet

  private def present(b: Bundle): RequirementResult =
    val t = "Outputs evaluated"
    (b.infra, b.audit) match
      case (None, _) | (_, None) => RequirementResult("R-ROC-PRESENT", t, Area, Verdict.NotRun, reason = "needs infra.jsonl and audit.jsonl")
      case (Some(infra), Some(_)) =>
        val have = rocIds(b) ++ b.degraded.getOrElse(Vector.empty).filter(_.record.component.value == "roc").map(_.record.interactionId.value)
        val missing = infra.filter(e => b.inWorkload(e.ts) && e.status >= 200 && e.status < 300)
          .flatMap(_.interactionId).distinct.filterNot(have.contains)
        if missing.isEmpty then RequirementResult("R-ROC-PRESENT", t, Area, Verdict.Satisfied, Vector("every successful model call has a ROC record"))
        else RequirementResult("R-ROC-PRESENT", t, Area, Verdict.Violated, missing, s"${missing.size} model outputs never evaluated")

  private def decision(b: Bundle): RequirementResult =
    val t = "Decision follows evidence"
    val rocs = b.audit.getOrElse(Vector.empty).map(_.record).filter(_.component.contains("roc"))
    if rocs.isEmpty then RequirementResult("R-ROC-DECISION", t, Area, Verdict.NotRun, reason = "no ROC records")
    else
      val problems = rocs.flatMap { r =>
        val id = r.interactionId.map(_.value).getOrElse(r.digest)
        val outcome = r.outcome.getOrElse("")
        val critical = r.interactionId.flatMap(i => b.tierOf(i.value)).contains("Critical")
        val allOk = r.entries.forall(_.satisfied)
        val mechFail = r.entries.exists(e => e.evaluabilityClass == EvaluabilityClass.Mechanical && !e.satisfied)
        val overTau = r.entries.exists(e => e.detail match
          case ClassifierDetail(_, _, score, th, _) => score >= th
          case _ => false)
        Vector(
          Option.when(allOk && outcome != "pass")(s"$id: all satisfied but outcome $outcome"),
          Option.when(!allOk && outcome == "pass")(s"$id: unsatisfied entry delivered as pass"),
          Option.when(critical && mechFail && !(outcome == "block" || outcome.startsWith("fallback:")))(s"$id: Critical deterministic failure, outcome $outcome"),
          Option.when(overTau && outcome == "pass")(s"$id: classifier at or over threshold delivered as pass")
        ).flatten
      }
      if problems.isEmpty then RequirementResult("R-ROC-DECISION", t, Area, Verdict.Satisfied, Vector(s"${rocs.size} decisions consistent"))
      else RequirementResult("R-ROC-DECISION", t, Area, Verdict.Violated, problems, s"${problems.size} inconsistent decisions")

  private def thresholds(b: Bundle): RequirementResult =
    val t = "Threshold monotonicity"
    val configs = b.configs.values.flatMap(f => ConfigLoader.loadFromString(f.text).toOption).toVector
    val all = configs.flatMap(_.thresholds)
    if all.isEmpty then RequirementResult("R-ROC-THRESH", t, Area, Verdict.NotRun, reason = "no thresholds")
    else
      val problems = configs.flatMap { c =>
        val ent = c.thresholds.filter(th => th.level == "enterprise" && th.property != "tau_max").map(th => th.property -> th.flagThreshold).toMap
        val tauMax = c.thresholds.find(_.property == "tau_max").map(_.flagThreshold)
        c.thresholds.filter(_.property != "tau_max").flatMap { th =>
          Vector(
            ent.get(th.property).filter(e => th.level != "enterprise" && th.flagThreshold > e)
              .map(e => s"${c.project}: ${th.property} at ${th.level} is ${th.flagThreshold} > enterprise $e"),
            tauMax.filter(m => th.riskTier == "Critical" && th.flagThreshold > m)
              .map(m => s"${c.project}: ${th.property} ${th.flagThreshold} > tau_max $m")
          ).flatten
        }
      }.distinct
      if problems.isEmpty then RequirementResult("R-ROC-THRESH", t, Area, Verdict.Satisfied, Vector(s"${all.size} thresholds monotonic"))
      else RequirementResult("R-ROC-THRESH", t, Area, Verdict.Violated, problems, s"${problems.size} threshold problems")

  private def posture(b: Bundle): RequirementResult =
    val t = "Failure posture"
    b.degraded match
      case None | Some(Vector()) => RequirementResult("R-POSTURE", t, Area, Verdict.NotRun, reason = "no degraded records")
      case Some(ds) =>
        ChainChecks.supervisorKms(b) match
          case Left(err) => RequirementResult("R-POSTURE", t, Area, Verdict.NotRun, reason = err)
          case Right(kms) =>
            val problems = ds.flatMap { d =>
              val id = d.record.interactionId.value
              val expected = b.tierOf(id) match
                case Some("Critical") => Some((FailurePosture.FailClosed, FailureAction.Blocked))
                case Some("Standard") => Some((FailurePosture.FailOpenFlagged, FailureAction.ProceededFlagged))
                case Some("Low") => Some((FailurePosture.FailOpen, FailureAction.ProceededSilent))
                case _ => None
              Vector(
                Option.when(expected.isEmpty)(s"$id: no tier (not in GIL or unknown project)"),
                expected.filter(e => e != (d.record.postureApplied, d.record.actionTaken))
                  .map(e => s"$id: posture ${d.record.postureApplied}/${d.record.actionTaken}, tier requires ${e._1}/${e._2}"),
                Option.when(!SignedDegradedRecord.verify(d, kms).contains(true))(s"$id: supervisor signature does not verify")
              ).flatten
            }
            if problems.isEmpty then RequirementResult("R-POSTURE", t, Area, Verdict.Satisfied, Vector(s"${ds.size} degraded records correct"))
            else RequirementResult("R-POSTURE", t, Area, Verdict.Violated, problems, s"${problems.size} posture problems")
