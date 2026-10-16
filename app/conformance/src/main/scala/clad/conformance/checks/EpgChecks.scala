package clad.conformance.checks

import clad.conformance.*
import clad.core.*
import clad.evaluation.*
import clad.runtime.{AuditEntry, EvaluabilityClass}

object EpgChecks:
  private val Area = "epg"

  def all(b: Bundle): Vector[RequirementResult] =
    val epg = b.audit.getOrElse(Vector.empty).filter(_.record.component.contains("epg"))
    if epg.isEmpty then
      Vector(RequirementResult("R-EPG-REEVAL", "EPG re-evaluation", Area, Verdict.NotRun, reason = "no EPG records"),
             RequirementResult("R-EPG-BLOCK", "Violations blocked", Area, Verdict.NotRun, reason = "no EPG records"))
    else Vector(reeval(b, epg.map(_.record)), block(b, epg.map(_.record)))

  private def reeval(b: Bundle, recs: Vector[clad.runtime.AuditRecord]): RequirementResult =
    val t = "EPG re-evaluation"
    val loaded = ConfigChecks.loaded(b)
    val problems = recs.flatMap { r =>
      val who = r.interactionId.map(_.value).getOrElse(r.digest)
      (loaded.get(r.configDigest), b.artifacts.get(r.artifactDigest)) match
        case (None, _) => Vector(s"$who: config ${r.configDigest} not in bundle")
        case (Some(Left(e)), _) => Vector(s"$who: config does not load: $e")
        case (_, None) => Vector(s"$who: artifact ${r.artifactDigest} not in bundle")
        case (Some(Right((config, eh))), Some(art)) =>
          val humans = config.agents.filter(_.kind == "human").map(_.id).toSet
          val procEntries = r.entries.filter(_.evaluabilityClass == EvaluabilityClass.Procedural)
          val evidence = EvidenceSet(procEntries.collect { case AuditEntry(c, _, _, sat, ProceduralDetail(att, at, why), _) =>
            c.property -> ProceduralEvidence(c.property, att, sat, at, why) }.toMap)
          val badAttestors = procEntries.collect { case AuditEntry(c, _, _, _, ProceduralDetail(att, _, _), _) if !humans.contains(att) =>
            s"$who: ${c.property.value} attested by non-human $att" }
          PromptEvaluator.evaluate(PromptArtifact(art.content, art.metadata), eh, evidence, Level.Project, r.timestamp) match
            case Left(e) => badAttestors :+ s"$who: $e"
            case Right(report) =>
              val expected = report.mechanicalResults.map(cr => cr.constraint -> cr.satisfied).toMap
              val recorded = r.entries.filter(_.evaluabilityClass == EvaluabilityClass.Mechanical).map(e => e.constraint -> e.satisfied).toMap
              val diff = (expected.keySet ++ recorded.keySet).toVector.filter(c => expected.get(c) != recorded.get(c))
                .map(c => s"$who: ${c.property.value} recorded ${recorded.get(c)} re-evaluated ${expected.get(c)}")
              badAttestors ++ diff
    }
    if problems.isEmpty then RequirementResult("R-EPG-REEVAL", t, Area, Verdict.Satisfied, Vector(s"${recs.size} EPG records re-evaluated"))
    else RequirementResult("R-EPG-REEVAL", t, Area, Verdict.Violated, problems, s"${problems.size} disagreements")

  private def block(b: Bundle, recs: Vector[clad.runtime.AuditRecord]): RequirementResult =
    val t = "Violations blocked"
    val rocIds = b.audit.getOrElse(Vector.empty).filter(_.record.component.contains("roc")).flatMap(_.record.interactionId.map(_.value)).toSet
    val problems = recs.filter(_.entries.exists(!_.satisfied)).flatMap { r =>
      val id = r.interactionId.map(_.value).getOrElse(r.digest)
      Vector(
        Option.when(!r.outcome.contains("block"))(s"$id: unsatisfied constraint but outcome ${r.outcome.getOrElse("none")}"),
        Option.when(rocIds.contains(id))(s"$id: blocked by EPG yet a ROC record exists (the model was called)")
      ).flatten
    }
    if problems.isEmpty then RequirementResult("R-EPG-BLOCK", t, Area, Verdict.Satisfied, Vector("every EPG violation blocked before the model"))
    else RequirementResult("R-EPG-BLOCK", t, Area, Verdict.Violated, problems, s"${problems.size} problems")
