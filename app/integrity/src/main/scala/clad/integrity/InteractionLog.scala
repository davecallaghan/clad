package clad.integrity

import java.time.Instant

// InteractionId moved to clad.core so that clad.runtime.AuditRecord can carry it:
// integrity depends on runtime, so the identifier could not live here and also be a
// field of an audit record. Aliased rather than re-declared so the 80-odd references
// in this package and in monitoring are unaffected.
type InteractionId = clad.core.InteractionId
val InteractionId: clad.core.InteractionId.type = clad.core.InteractionId

case class GilEntry(
  interactionId: InteractionId,
  registeredAt: Instant,
  metadata: Map[String, String] = Map.empty
)

trait InteractionLog[F[_]]:
  def register(entry: GilEntry): F[Unit]
  def exists(id: InteractionId): F[Boolean]
  def entriesBetween(start: Instant, end: Instant): F[Vector[GilEntry]]
  def count: F[Int]
