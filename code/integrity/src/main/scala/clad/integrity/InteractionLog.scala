package clad.integrity

import java.time.Instant

opaque type InteractionId = String
object InteractionId:
  def apply(value: String): InteractionId = value
  def generate(): InteractionId = java.util.UUID.randomUUID().toString
  extension (id: InteractionId) def value: String = id

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
