package clad.audit.test

import clad.audit.*
import java.time.Instant
import scala.util.{Try, Success}

class InMemoryAuditStore extends AuditStore[Try]:
  private var records: Vector[SignedAuditRecord] = Vector.empty

  def append(record: SignedAuditRecord): Try[Unit] = Try { records = records :+ record }
  def readAll: Try[Vector[SignedAuditRecord]] = Success(records)
  def readSince(timestamp: Instant): Try[Vector[SignedAuditRecord]] =
    Success(records.filter(_.record.timestamp.compareTo(timestamp) >= 0))
  def count: Try[Int] = Success(records.size)
  def clear(): Unit = records = Vector.empty
