package clad.audit

import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.InMemoryKeyManagement
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.util.{Try, Success}

class AppendOnlyFileStoreSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _
  private given Lift[Try] = Lift.given_Lift_Try

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("clad-audit-test")

  override def afterEach(): Unit =
    if tempDir != null && Files.exists(tempDir) then
      Files.walk(tempDir).iterator().forEachRemaining { path =>
        if Files.isRegularFile(path) then Files.delete(path)
      }
      Files.deleteIfExists(tempDir.resolve("chain.jsonl.lock"))
      Files.deleteIfExists(tempDir.resolve("chain.jsonl"))
      Files.deleteIfExists(tempDir)

  private val kms: KeyManagementService = InMemoryKeyManagement.default

  private def makeRecord(timestamp: Instant, artifactDigest: String = "sha256:artifact123"): SignedAuditRecord =
    val constraint = Constraint.Obligation(PropertyId.unsafe("test_property"), Level.Enterprise)
    val detail = MechanicalDetail(propertyDetected = true)
    val entry = AuditEntry(constraint, "1.0", EvaluabilityClass.Mechanical, satisfied = true, detail, timestamp)
    val record = AuditRecord(artifactDigest, Vector(entry), "sha256:config", timestamp, None)
    SignedAuditRecord.sign(record, kms).toOption.get

  test("append and read back a single record"):
    val store = AppendOnlyFileStore[Try](tempDir)
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val record = makeRecord(t1, "artifact-1")

    store.append(record) shouldBe Success(())
    val all = store.readAll.get
    all.size shouldBe 1
    all(0).record.artifactDigest shouldBe "artifact-1"

  test("preserve insertion order across multiple appends"):
    val store = AppendOnlyFileStore[Try](tempDir)
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val t2 = Instant.parse("2024-01-01T11:00:00Z")
    val t3 = Instant.parse("2024-01-01T12:00:00Z")

    store.append(makeRecord(t1, "artifact-1"))
    store.append(makeRecord(t2, "artifact-2"))
    store.append(makeRecord(t3, "artifact-3"))

    val all = store.readAll.get
    all.size shouldBe 3
    all(0).record.artifactDigest shouldBe "artifact-1"
    all(1).record.artifactDigest shouldBe "artifact-2"
    all(2).record.artifactDigest shouldBe "artifact-3"

  test("count returns correct count"):
    val store = AppendOnlyFileStore[Try](tempDir)
    store.count shouldBe Success(0)

    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    store.append(makeRecord(t1))
    store.count shouldBe Success(1)

    store.append(makeRecord(t1))
    store.count shouldBe Success(2)

  test("readSince filters by timestamp"):
    val store = AppendOnlyFileStore[Try](tempDir)
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val t2 = Instant.parse("2024-01-01T11:00:00Z")
    val t3 = Instant.parse("2024-01-01T12:00:00Z")

    store.append(makeRecord(t1))
    store.append(makeRecord(t2))
    store.append(makeRecord(t3))

    val result = store.readSince(t2).get
    result.size shouldBe 2
    result(0).record.timestamp shouldBe t2
    result(1).record.timestamp shouldBe t3

  test("persist across store instances"):
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val record = makeRecord(t1, "artifact-persistent")

    val store1 = AppendOnlyFileStore[Try](tempDir)
    store1.append(record) shouldBe Success(())

    val store2 = AppendOnlyFileStore[Try](tempDir)
    val all = store2.readAll.get
    all.size shouldBe 1
    all(0).record.artifactDigest shouldBe "artifact-persistent"

  test("write one line per record in chain.jsonl"):
    val store = AppendOnlyFileStore[Try](tempDir)
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val t2 = Instant.parse("2024-01-01T11:00:00Z")

    store.append(makeRecord(t1))
    store.append(makeRecord(t2))

    val chainFile = tempDir.resolve("chain.jsonl")
    val lines = Files.readAllLines(chainFile)
    lines.size shouldBe 2
    lines.get(0) should not be empty
    lines.get(1) should not be empty

  test("detect corrupted lines and throw CorruptionDetected"):
    val store = AppendOnlyFileStore[Try](tempDir)
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    store.append(makeRecord(t1))

    // Manually append garbage
    val chainFile = tempDir.resolve("chain.jsonl")
    Files.write(chainFile, "\n{invalid json}\n".getBytes("UTF-8"),
      java.nio.file.StandardOpenOption.APPEND)

    val result = store.readAll
    result.isFailure shouldBe true
    result.failed.get shouldBe a[CorruptionDetected]

  test("AuditRecordCodec round-trip preserves all fields including signature bytes"):
    val t1 = Instant.parse("2024-01-01T10:00:00Z")
    val original = makeRecord(t1, "artifact-roundtrip")

    val encoded = AuditRecordCodec.encode(original)
    val decoded = AuditRecordCodec.decode(encoded)

    decoded shouldBe a[Right[_, _]]
    val restored = decoded.toOption.get

    restored.record.artifactDigest shouldBe original.record.artifactDigest
    restored.record.timestamp shouldBe original.record.timestamp
    restored.signature.value shouldBe original.signature.value
    restored.signature.keyId shouldBe original.signature.keyId
    restored.signature.algorithm shouldBe original.signature.algorithm

  test("AuditRecordCodec returns Left for invalid JSON"):
    val result = AuditRecordCodec.decode("{invalid json}")
    result shouldBe a[Left[_, _]]

    val result2 = AuditRecordCodec.decode("")
    result2 shouldBe a[Left[_, _]]
