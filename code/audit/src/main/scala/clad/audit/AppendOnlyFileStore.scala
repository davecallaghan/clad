package clad.audit

import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.channels.FileChannel
import java.time.Instant
import scala.util.{Try, Using}
import scala.jdk.CollectionConverters.*

class AppendOnlyFileStore[F[_]](directory: Path)(using lift: Lift[F]) extends AuditStore[F]:

  private val chainFile = directory.resolve("chain.jsonl")
  private val lockFile = directory.resolve("chain.jsonl.lock")

  private def ensureDirectory(): Unit =
    if !Files.exists(directory) then Files.createDirectories(directory)
    if !Files.exists(chainFile) then Files.createFile(chainFile)

  def append(record: SignedAuditRecord): F[Unit] =
    lift.fromTry(Try {
      ensureDirectory()
      val line = AuditRecordCodec.encode(record) + "\n"
      Using(FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) { channel =>
        val lock = channel.lock()
        try
          Files.write(chainFile, line.getBytes("UTF-8"), StandardOpenOption.APPEND)
          ()
        finally lock.release()
      }.get
    })

  def readAll: F[Vector[SignedAuditRecord]] =
    lift.fromTry(Try {
      ensureDirectory()
      val lines = Files.readAllLines(chainFile).asScala.toVector.filter(_.nonEmpty)
      lines.zipWithIndex.map { (line, idx) =>
        AuditRecordCodec.decode(line) match
          case Right(record) => record
          case Left(err) => throw CorruptionDetected(s"Line $idx: $err")
      }
    })

  def readSince(timestamp: Instant): F[Vector[SignedAuditRecord]] =
    lift.fromTry(Try {
      ensureDirectory()
      Files.readAllLines(chainFile).asScala.toVector.filter(_.nonEmpty).zipWithIndex.flatMap { (line, idx) =>
        AuditRecordCodec.decode(line) match
          case Right(record) if record.record.timestamp.compareTo(timestamp) >= 0 => Some(record)
          case Right(_) => None
          case Left(err) => throw CorruptionDetected(s"Line $idx: $err")
      }
    })

  def count: F[Int] =
    lift.fromTry(Try {
      ensureDirectory()
      Files.readAllLines(chainFile).asScala.count(_.nonEmpty)
    })
