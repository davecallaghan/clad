package clad.gateway.epg

import clad.config.ConfigLoader
import clad.core.{Level, PropertyId}
import clad.evaluation.{ConstraintResult, EvaluableHierarchy, EvidenceSet, PromptArtifact, PromptEvaluator, ProceduralEvidence}
import clad.gateway.config.{GatewayConfig, Template}
import clad.runtime.{AuditEntry, EvaluabilityClass}
import java.time.Instant

final case class EpgResult(entries: Vector[AuditEntry], artifactDigest: String, artifactJson: String,
                           configDigest: String, configJson: String):
  def blocked: Boolean = entries.exists(!_.satisfied)

/** Prompt governance evaluated by clad's own evaluator over the hierarchy clad's ConfigLoader builds from the
  * exported config, the path conformance re-evaluates (R-EPG-REEVAL), so the two agree by construction. */
final class Epg private (cfg: GatewayConfig, hierarchies: Map[String, EvaluableHierarchy]):

  def evaluate(t: Template, rendered: String, model: String, at: Instant): Either[String, EpgResult] =
    for
      hierarchy <- hierarchies.get(t.project).toRight(s"${t.id}: unknown project ${t.project}")
      metadata = cfg.templates.metadata(t, model) + ("project" -> t.project)
      evidence <- evidenceFor(t)
      artifact = PromptArtifact(rendered, metadata)
      report <- PromptEvaluator.evaluate(artifact, hierarchy, evidence, Level.Project, at)
        .left.map(e => s"${t.id}: evaluation failed: $e")
    yield
      val entries = (report.mechanicalResults.toVector.map(entry(_, EvaluabilityClass.Mechanical, at)) ++
        report.proceduralResults.toVector.map(entry(_, EvaluabilityClass.Procedural, at))).sortBy(_.constraint.property.value)
      val artifactJson = ujson.write(ujson.Obj("content" -> rendered,
        "metadata" -> ujson.Obj.from(metadata.toSeq.sortBy(_._1).map((k, v) => k -> ujson.Str(v)))))
      EpgResult(entries, artifact.digest, artifactJson,
        cfg.governance.configDigest(t.project, cfg.roc), cfg.governance.configJson(t.project, cfg.roc))

  private def entry(r: ConstraintResult, c: EvaluabilityClass, at: Instant): AuditEntry =
    AuditEntry(r.constraint, r.version, c, r.satisfied, r.detail, at)

  private def evidenceFor(t: Template): Either[String, EvidenceSet] =
    val att = cfg.attestations.get(t.id, t.version)
    val procedural = cfg.governance.path(t.project).filter(_.evaluability == "procedural")
    procedural.foldLeft[Either[String, Map[PropertyId, ProceduralEvidence]]](Right(Map.empty)) { (acc, c) =>
      for
        m <- acc
        prop <- PropertyId(c.property).left.map(e => s"${t.id}: invalid property ${e.value}")
      yield m + (prop -> (att match
        case Some(a) => ProceduralEvidence(prop, a.attestor, true, a.attestedAt, a.rationale)
        case None    => ProceduralEvidence(prop, "", false, Instant.EPOCH, s"no attestation for ${t.id}@${t.version}")))
    }.map(EvidenceSet(_))

object Epg:
  def build(cfg: GatewayConfig): Either[String, Epg] =
    cfg.governance.projects.keys.toVector.sorted.foldLeft[Either[String, Map[String, EvaluableHierarchy]]](Right(Map.empty)) {
      (acc, p) =>
        acc.flatMap { m =>
          ConfigLoader.loadFromString(cfg.governance.configJson(p, cfg.roc)).flatMap(ConfigLoader.buildHierarchy)
            .left.map(es => s"project $p: ${es.mkString("; ")}").map(h => m + (p -> h))
        }
    }.map(Epg(cfg, _))
