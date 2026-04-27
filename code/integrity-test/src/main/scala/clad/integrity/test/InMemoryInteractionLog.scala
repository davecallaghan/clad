package clad.integrity.test

import clad.integrity.*
import java.time.Instant
import scala.util.{Try, Success}

class InMemoryInteractionLog extends InteractionLog[Try]:
  private var entries: Vector[GilEntry] = Vector.empty

  def register(entry: GilEntry): Try[Unit] = Try { entries = entries :+ entry }
  def exists(id: InteractionId): Try[Boolean] = Success(entries.exists(_.interactionId == id))
  def entriesBetween(start: Instant, end: Instant): Try[Vector[GilEntry]] =
    Success(entries.filter(e => !e.registeredAt.isBefore(start) && !e.registeredAt.isAfter(end)))
  def count: Try[Int] = Success(entries.size)
  def clear(): Unit = entries = Vector.empty
  def allEntries: Vector[GilEntry] = entries
