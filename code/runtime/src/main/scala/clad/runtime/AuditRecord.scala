package clad.runtime

import clad.core.*
import clad.evaluation.*
import java.time.Instant
import java.security.MessageDigest

enum EvaluabilityClass:
  case Mechanical, Procedural

case class AuditEntry(
  constraint: Constraint,
  constraintVersion: String,
  evaluabilityClass: EvaluabilityClass,
  satisfied: Boolean,
  detail: EvalDetail,
  timestamp: Instant
)

case class AuditRecord(
  artifactDigest: String,
  entries: Vector[AuditEntry],
  configDigest: String,
  timestamp: Instant,
  previousDigest: Option[String]
):
  lazy val digest: String =
    val canonical = s"$artifactDigest|$configDigest|$timestamp|$previousDigest|" +
      entries.map(e =>
        s"${e.constraint.property.value}:${e.constraintVersion}:${e.evaluabilityClass}:${e.satisfied}"
      ).sorted.mkString("|")
    val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes("UTF-8"))
    "sha256:" + bytes.map("%02x".format(_)).mkString

  def isComplete(expectedConstraints: Set[Constraint]): Boolean =
    entries.map(_.constraint).toSet == expectedConstraints

  def entryCount: Int = entries.size
  def satisfiedCount: Int = entries.count(_.satisfied)
  def unsatisfiedEntries: Vector[AuditEntry] = entries.filterNot(_.satisfied)

case class AuditChain private (records: Vector[AuditRecord]):
  def append(record: AuditRecord): AuditChain =
    val linked = record.copy(previousDigest = latest.map(_.digest))
    AuditChain(records :+ linked)

  def latest: Option[AuditRecord] = records.lastOption
  def length: Int = records.size

  def isIntegral: Boolean =
    if records.isEmpty then true
    else if records.head.previousDigest.isDefined then false
    else records.zip(records.tail).forall { (prev, curr) =>
      curr.previousDigest.contains(prev.digest)
    }

object AuditChain:
  val empty: AuditChain = AuditChain(Vector.empty)
