package clad.evaluation

import clad.core.*
import java.time.Instant

case class ProceduralEvidence(
  constraintProperty: PropertyId,
  attestor: String,
  satisfied: Boolean,
  attestedAt: Instant,
  rationale: String
)

case class EvidenceSet(evidence: Map[PropertyId, ProceduralEvidence]):
  def forProperty(pid: PropertyId): Option[ProceduralEvidence] =
    evidence.get(pid)

object EvidenceSet:
  val empty: EvidenceSet = EvidenceSet(Map.empty)
  def of(items: ProceduralEvidence*): EvidenceSet =
    EvidenceSet(items.map(e => e.constraintProperty -> e).toMap)
