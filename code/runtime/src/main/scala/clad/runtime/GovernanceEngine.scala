package clad.runtime

import clad.core.*
import clad.evaluation.*
import java.time.Instant
import java.security.MessageDigest

case class EngineConfig(
  hierarchy: EvaluableHierarchy,
  evidence: EvidenceSet,
  level: Level,
  metadata: Map[String, String] = Map.empty
)

object GovernanceEngine:
  sealed trait EngineError
  case class EvidenceGap(missing: Set[PropertyId]) extends EngineError

  sealed trait EvaluationFailure
  case class MissingEvidence(properties: Set[PropertyId]) extends EvaluationFailure

  private val EngineVersion = "0.1.0-SNAPSHOT"

  def build(config: EngineConfig): Either[List[EngineError], GovernanceEngine] =
    val errors = List.newBuilder[EngineError]
    val proceduralProps = config.hierarchy.proceduralAt(config.level).map(_.constraint.property)
    val evidenceGaps = proceduralProps.filter(p => config.evidence.forProperty(p).isEmpty)
    if evidenceGaps.nonEmpty then
      errors += EvidenceGap(evidenceGaps)
    val errs = errors.result()
    if errs.nonEmpty then Left(errs)
    else Right(new GovernanceEngine(config))

class GovernanceEngine private (config: EngineConfig):
  import GovernanceEngine.*

  private val configDigest: String =
    val checkerVersions = config.hierarchy.checkerRegistry.checkers.keys
      .toList.map(_.value).sorted.mkString(",")
    val canonical = s"${config.level}|$checkerVersions"
    val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes("UTF-8"))
    "sha256:" + bytes.map("%02x".format(_)).mkString

  def evaluate(
    prompt: String,
    promptMeta: Map[String, String] = Map.empty
  ): Either[EvaluationFailure, GovernanceReport] =
    val artifact = PromptArtifact(prompt, promptMeta)
    val now = Instant.now()
    PromptEvaluator.evaluate(artifact, config.hierarchy, config.evidence, config.level, now) match
      case Left(PromptEvaluator.EvaluationError.MissingEvidence(props)) =>
        Left(MissingEvidence(props))
      case Right(evalReport) =>
        val artifactDigest = sha256(prompt)
        val auditEntries = evalReport.results.toVector.map { cr =>
          val evalClass = if evalReport.mechanicalResults.contains(cr) then
            EvaluabilityClass.Mechanical else EvaluabilityClass.Procedural
          AuditEntry(cr.constraint, cr.version, evalClass, cr.satisfied, cr.detail, now)
        }
        val audit = AuditRecord(artifactDigest, auditEntries, configDigest, now, None)
        Right(GovernanceReport(evalReport, artifact, audit, EngineVersion, configDigest))

  private def sha256(content: String): String =
    val bytes = MessageDigest.getInstance("SHA-256").digest(content.getBytes("UTF-8"))
    "sha256:" + bytes.map("%02x".format(_)).mkString
