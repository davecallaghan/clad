package clad.audit

import java.nio.file.{Files, Path, StandardOpenOption}
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.util.concurrent.locks.ReentrantLock
import java.time.Instant
import scala.util.{Try, Using}
import scala.jdk.CollectionConverters.*

// kms is optional so existing call sites keep compiling. A store without one
// writes no anchor, and verifyAgainstAnchor reports AnchorMissing for it, which
// is the correct verdict.
class AppendOnlyFileStore[F[_]](directory: Path, kms: Option[KeyManagementService] = None)(using lift: Lift[F]) extends AuditStore[F]:

  private val chainFile = directory.resolve("chain.jsonl")
  private val lockFile = directory.resolve("chain.jsonl.lock")
  private val anchorStore = ChainAnchorStore(directory)

  // FileChannel locks are per-JVM: a second lock() on an overlapping region throws
  // instead of queueing. This serializes threads in-process; the file lock guards
  // other processes only.
  private val writeLock = ReentrantLock()
  private val lockRetries = 20
  private val lockBackoffMillis = 25L

  // Creates the directory only. An exists-then-createFile pair is a race: under
  // concurrent append every thread sees the file absent and all but one fail with
  // FileAlreadyExistsException. The file is created on demand by CREATE+APPEND.
  private def ensureDirectory(): Unit =
    if !Files.exists(directory) then Files.createDirectories(directory)

  def append(record: SignedAuditRecord): F[Unit] =
    lift.fromTry(Try {
      val line = AuditRecordCodec.encode(record) + "\n"
      writeLock.lock()
      try
        ensureDirectory()
        appendUnderFileLock(line)
        updateAnchor()
      finally writeLock.unlock()
    })

  // Written while writeLock is held, so the anchor cannot drift from the chain.
  private def updateAnchor(): Unit =
    kms.foreach { k =>
      val decoded = Files.readAllLines(chainFile).asScala.toVector
        .filter(_.nonEmpty)
        .flatMap(l => AuditRecordCodec.decode(l).toOption)
      ChainAnchor.sign(ChainAnchor.forChain(decoded, Instant.now()), k)
        .foreach(anchorStore.write(_).get)
    }

  private def appendUnderFileLock(line: String): Unit =
    var attempt = 0
    var written = false
    while !written do
      try
        Using(FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) { channel =>
          val lock = channel.lock()
          try
            Files.write(chainFile, line.getBytes("UTF-8"),
              StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            ()
          finally lock.release()
        }.get
        written = true
      catch
        case _: OverlappingFileLockException if attempt < lockRetries =>
          attempt += 1
          Thread.sleep(lockBackoffMillis)
    ()

  def readAll: F[Vector[SignedAuditRecord]] =
    lift.fromTry(Try {
      ensureDirectory()
      val lines =
        if !Files.exists(chainFile) then Vector.empty
        else Files.readAllLines(chainFile).asScala.toVector.filter(_.nonEmpty)
      lines.zipWithIndex.map { (line, idx) =>
        AuditRecordCodec.decode(line) match
          case Right(record) => record
          case Left(err) => throw CorruptionDetected(s"Line $idx: $err")
      }
    })

  def readSince(timestamp: Instant): F[Vector[SignedAuditRecord]] =
    lift.fromTry(Try {
      ensureDirectory()
      (if !Files.exists(chainFile) then Vector.empty
       else Files.readAllLines(chainFile).asScala.toVector.filter(_.nonEmpty)).zipWithIndex.flatMap { (line, idx) =>
        AuditRecordCodec.decode(line) match
          case Right(record) if record.record.timestamp.compareTo(timestamp) >= 0 => Some(record)
          case Right(_) => None
          case Left(err) => throw CorruptionDetected(s"Line $idx: $err")
      }
    })

  def count: F[Int] =
    lift.fromTry(Try {
      ensureDirectory()
      if !Files.exists(chainFile) then 0
      else Files.readAllLines(chainFile).asScala.count(_.nonEmpty)
    })
