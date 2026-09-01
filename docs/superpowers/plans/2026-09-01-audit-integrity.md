# Audit Integrity Implementation Plan (Plan 1 of 5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the three audit defects that produce a false clean verdict detectable — a truncated chain, a lost record under concurrent append, and a chain-integrity check that cannot fire.

**Architecture:** Three independent changes to the `audit` module, each ending in a passing test that fails first. The record's digest becomes a persisted, signed value so recomputation can disagree with it; appends serialize through an in-process lock with bounded retry on cross-process contention; and a signed anchor records the chain's length and head digest so a shortened chain no longer verifies.

**Tech Stack:** Scala 3.3.7, sbt, ScalaTest 3.2.19 (`AnyFlatSpec` + `Matchers`), upickle 4.1.0 for JSON, `java.security.MessageDigest` (SHA-256), `java.nio.channels.FileChannel` locking.

**Spec:** `docs/superpowers/specs/2026-09-01-clad-conformance-design.md` — this plan implements backlog items 1, 4 and 5.

## Global Constraints

- Scala 3 syntax throughout (indentation-based, `enum`, `given`/`using`). Match the surrounding files; do not introduce braces-style blocks.
- sbt project names: source module is `audit`, test module is `` `audit-test` `` (backticked in `build.sbt`). Run tests as `sbt "audit-test/testOnly <fqcn>"`.
- `AuditStore[F[_]]` is effect-polymorphic via `Lift[F]`. Only `Lift[Try]` is given. Do not add a new effect type in this plan.
- Digest format is the literal prefix `sha256:` followed by lowercase hex, produced by `MessageDigest.getInstance("SHA-256")`. Do not change the algorithm or the prefix.
- Every new failure case must extend `AuditVerifier.VerificationFailure` and must set `firstFailureAt` through the existing `recordFailure` helper, so `isFullyVerified` becomes false.
- Commit after each task with the message given in that task's final step.

---

### Task 1: Persist the record digest so the integrity check can fire

`AuditRecord.digest` is a `lazy val` computed from the record's own fields, and `AuditRecordCodec` never writes it. `AuditVerifier.recomputeDigest` duplicates the same formula, so after a round trip `recomputeDigest(record) == record.digest` holds by construction and Check 1 at `AuditVerifier.scala:42-44` can never fire. Tamper detection is done entirely by the signature, which means `chainIntegral` can read `true` on tampered content.

The fix carries the digest as it was written, alongside the record, so recomputation has something independent to disagree with.

**Files:**
- Modify: `code/audit/src/main/scala/clad/audit/SignedAuditRecord.scala`
- Modify: `code/audit/src/main/scala/clad/audit/AuditRecordCodec.scala:60-63`
- Modify: `code/audit/src/main/scala/clad/audit/AuditVerifier.scala:18,40-45`
- Test: `code/audit-test/src/test/scala/clad/audit/AuditVerifierSpec.scala`

**Interfaces:**
- Consumes: `AuditRecord` (fields `artifactDigest: String`, `entries: Vector[AuditEntry]`, `configDigest: String`, `timestamp: Instant`, `previousDigest: Option[String]`, and `lazy val digest: String`); `KeyManagementService.sign(bytes: Array[Byte]): Either[KmsError, Signature]`.
- Produces: `SignedAuditRecord(record: AuditRecord, signature: Signature, recordedDigest: Option[String])`; new failure case `AuditVerifier.MissingRecordedDigest(index: Int)`. Tasks 2 and 3 construct `SignedAuditRecord` and must pass all three arguments.

- [ ] **Step 1: Write the failing test**

Add to `code/audit-test/src/test/scala/clad/audit/AuditVerifierSpec.scala`:

```scala
it should "report DigestMismatch when persisted content is tampered after signing" in {
  val kms = InMemoryKms()
  val record = TestRecords.simple(previousDigest = None)
  val signed = SignedAuditRecord.sign(record, kms).toOption.get

  // Tamper a field the digest is computed over, keeping the digest as written.
  val tampered = signed.copy(record = signed.record.copy(artifactDigest = "sha256:deadbeef"))

  val report = AuditVerifier.verify(Vector(tampered), kms)

  report.failures.collect { case d: AuditVerifier.DigestMismatch => d } should have size 1
  report.chainIntegral shouldBe false
  report.isFullyVerified shouldBe false
}

it should "report MissingRecordedDigest for a record written before digests were persisted" in {
  val kms = InMemoryKms()
  val record = TestRecords.simple(previousDigest = None)
  val signed = SignedAuditRecord.sign(record, kms).toOption.get
  val legacy = signed.copy(recordedDigest = None)

  val report = AuditVerifier.verify(Vector(legacy), kms)

  report.failures should contain(AuditVerifier.MissingRecordedDigest(0))
  report.isFullyVerified shouldBe false
}
```

If `TestRecords.simple` and `InMemoryKms` are not already present in `audit-test`, read `AuditVerifierSpec.scala` and reuse whatever fixture the existing tests use; do not create a second fixture.

- [ ] **Step 2: Run the test to verify it fails**

Run: `sbt "audit-test/testOnly clad.audit.AuditVerifierSpec"`
Expected: compilation failure — `value recordedDigest is not a member of SignedAuditRecord`, and `MissingRecordedDigest is not a member of object AuditVerifier`.

- [ ] **Step 3: Add the field and the failure case**

`SignedAuditRecord.scala` in full:

```scala
package clad.audit

import clad.runtime.AuditRecord

case class SignedAuditRecord(
  record: AuditRecord,
  signature: Signature,
  recordedDigest: Option[String]
):
  def isAuthentic(kms: KeyManagementService): Either[KmsError, Boolean] =
    kms.verify(record.digest.getBytes("UTF-8"), signature)

object SignedAuditRecord:
  def sign(record: AuditRecord, kms: KeyManagementService): Either[KmsError, SignedAuditRecord] =
    kms.sign(record.digest.getBytes("UTF-8"))
      .map(sig => SignedAuditRecord(record, sig, Some(record.digest)))
```

In `AuditVerifier.scala`, after the `DigestMismatch` case class on line 18, add:

```scala
  case class MissingRecordedDigest(index: Int) extends VerificationFailure
```

- [ ] **Step 4: Persist the field in the codec**

Replace the `SignedAuditRecord` given in `AuditRecordCodec.scala:60-63` with:

```scala
  private given ReadWriter[SignedAuditRecord] = readwriter[ujson.Value].bimap(
    sar => ujson.Obj(
      "record" -> writeJs(sar.record),
      "signature" -> writeJs(sar.signature),
      "recordedDigest" -> (sar.recordedDigest match { case Some(d) => ujson.Str(d); case None => ujson.Null })
    ),
    json => SignedAuditRecord(
      read[AuditRecord](json("record")),
      read[Signature](json("signature")),
      json.obj.get("recordedDigest").flatMap {
        case ujson.Null => None
        case v => Some(v.str)
      }
    )
  )
```

`json.obj.get` rather than `json(...)` so a line written before this change decodes to `None` instead of throwing.

- [ ] **Step 5: Make Check 1 compare the persisted digest against a fresh recomputation**

Replace `AuditVerifier.scala:40-45` (the `// Check 1: Digest recomputation` block) with:

```scala
      // Check 1: the digest as written must match a fresh recomputation.
      // Comparing against record.digest would be vacuous: it is a lazy val over
      // the same fields recomputeDigest reads, so the two agree by construction.
      val recomputed = recomputeDigest(record)
      signed.recordedDigest match
        case Some(written) if written != recomputed =>
          recordFailure(idx, DigestMismatch(idx, written, recomputed))
          chainOk = false
        case None =>
          recordFailure(idx, MissingRecordedDigest(idx))
          chainOk = false
        case _ => ()
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `sbt "audit-test/testOnly clad.audit.AuditVerifierSpec"`
Expected: PASS, all tests in the spec.

Then run the whole suite, because adding a constructor parameter breaks every construction site:

Run: `sbt test`
Expected: PASS. If any module fails to compile on `SignedAuditRecord(...)` with two arguments, add `Some(record.digest)` as the third at that site — or `None` if the test is deliberately constructing a legacy record.

- [ ] **Step 7: Commit**

```bash
git add code/audit code/audit-test
git commit -m "fix(audit): persist the record digest so the integrity check can fire

AuditRecord.digest is a lazy val over the record's own fields and was never
written to disk, so recomputeDigest(record) == record.digest held by
construction and AuditVerifier's Check 1 could never fail. chainIntegral read
true on tampered content; only the signature was doing any work.

SignedAuditRecord now carries recordedDigest, the codec persists it, and
verify compares it against a fresh recomputation. A record written before this
change decodes to None and is reported as MissingRecordedDigest rather than
silently passing."
```

---

### Task 2: Serialize appends and retry on cross-process contention

`AppendOnlyFileStore.append` opens a `FileChannel` on the lock file and calls `channel.lock()`. `FileChannel.lock()` throws `OverlappingFileLockException` rather than blocking when an overlapping lock is already held **by the same JVM**. Two threads appending concurrently — plausible under the http4s server in `api/GovernanceRoutes.scala` — leave the losing thread with a `Failure` inside its `Try`, and no caller retries. The audit record is never persisted.

**Files:**
- Modify: `code/audit/src/main/scala/clad/audit/AppendOnlyFileStore.scala:1-28`
- Test: `code/audit-test/src/test/scala/clad/audit/AppendOnlyFileStoreSpec.scala`

**Interfaces:**
- Consumes: `SignedAuditRecord(record, signature, recordedDigest)` from Task 1; `AuditRecordCodec.encode`.
- Produces: no signature change. `AppendOnlyFileStore.append` gains the guarantee that concurrent callers within one JVM all persist.

- [ ] **Step 1: Write the failing test**

Add to `code/audit-test/src/test/scala/clad/audit/AppendOnlyFileStoreSpec.scala`:

```scala
it should "persist every record when appends are concurrent" in {
  val dir = Files.createTempDirectory("clad-audit-concurrent")
  val store = AppendOnlyFileStore[Try](dir)
  val kms = InMemoryKms()

  val threadCount = 8
  val records = (0 until threadCount).map { i =>
    SignedAuditRecord.sign(TestRecords.simple(previousDigest = None, salt = i.toString), kms).toOption.get
  }

  val latch = java.util.concurrent.CountDownLatch(1)
  val failures = java.util.concurrent.ConcurrentLinkedQueue[Throwable]()
  val threads = records.map { r =>
    val t = Thread(() => {
      latch.await()
      store.append(r) match
        case scala.util.Failure(e) => failures.add(e)
        case scala.util.Success(_) => ()
    })
    t.start(); t
  }
  latch.countDown()
  threads.foreach(_.join(10_000))

  failures.isEmpty shouldBe true
  store.count.get shouldBe threadCount
}
```

`TestRecords.simple` needs a `salt` so the eight records differ. If the existing fixture has no such parameter, add one with a default: `def simple(previousDigest: Option[String], salt: String = ""): AuditRecord` and fold `salt` into `artifactDigest`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `sbt "audit-test/testOnly clad.audit.AppendOnlyFileStoreSpec"`
Expected: FAIL — `failures.isEmpty` is false, with one or more `OverlappingFileLockException`, and `count` less than 8.

- [ ] **Step 3: Add an in-process lock and bounded retry**

In `AppendOnlyFileStore.scala`, add the import and a lock field, then replace `append`:

```scala
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.util.concurrent.locks.ReentrantLock
```

```scala
  // FileChannel locks are per-JVM: a second lock() on an overlapping region
  // throws instead of queueing. The in-process lock serializes threads here;
  // the file lock guards other processes only.
  private val writeLock = ReentrantLock()

  private val lockRetries = 20
  private val lockBackoffMillis = 25L

  def append(record: SignedAuditRecord): F[Unit] =
    lift.fromTry(Try {
      ensureDirectory()
      val line = AuditRecordCodec.encode(record) + "\n"
      writeLock.lock()
      try appendUnderFileLock(line)
      finally writeLock.unlock()
    })

  private def appendUnderFileLock(line: String): Unit =
    var attempt = 0
    var written = false
    while !written do
      try
        Using(FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) { channel =>
          val lock = channel.lock()
          try
            Files.write(chainFile, line.getBytes("UTF-8"), StandardOpenOption.APPEND)
            ()
          finally lock.release()
        }.get
        written = true
      catch
        case _: OverlappingFileLockException if attempt < lockRetries =>
          attempt += 1
          Thread.sleep(lockBackoffMillis)
    ()
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `sbt "audit-test/testOnly clad.audit.AppendOnlyFileStoreSpec"`
Expected: PASS — no failures, `count` is 8.

- [ ] **Step 5: Commit**

```bash
git add code/audit code/audit-test
git commit -m "fix(audit): serialize appends within the JVM and retry on file-lock contention

FileChannel.lock() throws OverlappingFileLockException rather than blocking
when an overlapping lock is held by the same JVM, so two concurrent appends
left the loser with a Failure inside its Try and no caller retried. The audit
record was silently never persisted.

A ReentrantLock now serializes appends in-process, leaving the file lock to
guard other processes, with bounded retry for that case."
```

---

### Task 3: Anchor the chain so truncation is detectable

`AuditVerifier.verify` checks only the internal consistency of the vector it is handed. Delete the trailing lines of `chain.jsonl` and the first record still has `previousDigest = None`, every remaining link still matches its predecessor, every signature still verifies, and `isFullyVerified` returns **true**. There is no checkpoint, watermark or external anchor anywhere in the repository, and no test exercises truncation.

This implements AI5 (Chain Truncation Detection) from the book.

**Scope, stated honestly:** the anchor is signed, so forging it requires the KMS key, and it is written under the same lock as the append so it cannot drift from the chain. An attacker holding the signing key can still rewrite both consistently. This raises the cost from "delete lines" to "delete lines, rewrite the anchor, and hold the key"; it does not make the chain unforgeable.

**Files:**
- Create: `code/audit/src/main/scala/clad/audit/ChainAnchor.scala`
- Modify: `code/audit/src/main/scala/clad/audit/AppendOnlyFileStore.scala`
- Modify: `code/audit/src/main/scala/clad/audit/AuditVerifier.scala`
- Test: `code/audit-test/src/test/scala/clad/audit/ChainAnchorSpec.scala`

**Interfaces:**
- Consumes: `SignedAuditRecord(record, signature, recordedDigest)` and `MissingRecordedDigest` from Task 1; the `writeLock` and `appendUnderFileLock` from Task 2; `KeyManagementService.sign` / `.verify`.
- Produces:
  - `ChainAnchor(count: Int, headDigest: String, updatedAt: Instant)` with `def canonical: String`
  - `SignedChainAnchor(anchor: ChainAnchor, signature: Signature)`
  - `ChainAnchorStore(directory: Path)` with `def write(anchor: SignedChainAnchor): Try[Unit]` and `def read: Try[Option[SignedChainAnchor]]`
  - `AuditVerifier.verifyAgainstAnchor(records: Vector[SignedAuditRecord], anchor: Option[SignedChainAnchor], kms: KeyManagementService): VerificationReport`
  - new failure cases `AuditVerifier.TruncationDetected(expectedCount: Int, actualCount: Int)`, `AuditVerifier.AnchorHeadMismatch(expected: String, actual: String)`, `AuditVerifier.AnchorSignatureInvalid`, `AuditVerifier.AnchorMissing`

- [ ] **Step 1: Write the failing test**

Create `code/audit-test/src/test/scala/clad/audit/ChainAnchorSpec.scala`:

```scala
package clad.audit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.jdk.CollectionConverters.*
import scala.util.Try

class ChainAnchorSpec extends AnyFlatSpec with Matchers:

  private def appendChain(dir: Path, kms: KeyManagementService, n: Int): Unit =
    // The KMS is required for the store to write an anchor; Task 3 Step 4 adds
    // this parameter with a default, so stores built without one write none.
    val store = AppendOnlyFileStore[Try](dir, Some(kms))
    var prev: Option[String] = None
    (0 until n).foreach { i =>
      val rec = TestRecords.simple(previousDigest = prev, salt = i.toString)
      store.append(SignedAuditRecord.sign(rec, kms).toOption.get).get
      prev = Some(rec.digest)
    }

  private def dropLastLines(dir: Path, k: Int): Unit =
    val f = dir.resolve("chain.jsonl")
    val kept = Files.readAllLines(f).asScala.toVector.filter(_.nonEmpty).dropRight(k)
    Files.write(f, (kept.mkString("\n") + "\n").getBytes("UTF-8"))

  "an anchored chain" should "verify when intact" in {
    val dir = Files.createTempDirectory("clad-anchor-intact")
    val kms = InMemoryKms()
    appendChain(dir, kms, 5)

    // No KMS needed to read; a reader never writes an anchor.
    val records = AppendOnlyFileStore[Try](dir).readAll.get
    val anchor = ChainAnchorStore(dir).read.get

    val report = AuditVerifier.verifyAgainstAnchor(records, anchor, kms)
    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 5
  }

  it should "detect a chain whose tail has been deleted" in {
    val dir = Files.createTempDirectory("clad-anchor-truncated")
    val kms = InMemoryKms()
    appendChain(dir, kms, 5)
    dropLastLines(dir, 2)

    val records = AppendOnlyFileStore[Try](dir).readAll.get
    val anchor = ChainAnchorStore(dir).read.get

    // The surviving chain is internally consistent -- this is the point.
    AuditVerifier.verify(records, kms).isFullyVerified shouldBe true

    val report = AuditVerifier.verifyAgainstAnchor(records, anchor, kms)
    report.isFullyVerified shouldBe false
    report.failures should contain(AuditVerifier.TruncationDetected(5, 3))
  }

  it should "report AnchorMissing when no anchor is present" in {
    val dir = Files.createTempDirectory("clad-anchor-absent")
    val kms = InMemoryKms()
    val records = Vector.empty[SignedAuditRecord]
    val report = AuditVerifier.verifyAgainstAnchor(records, None, kms)
    report.failures should contain(AuditVerifier.AnchorMissing)
    report.isFullyVerified shouldBe false
  }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `sbt "audit-test/testOnly clad.audit.ChainAnchorSpec"`
Expected: compilation failure — `not found: ChainAnchorStore`, `value verifyAgainstAnchor is not a member of object AuditVerifier`.

- [ ] **Step 3: Create the anchor types and store**

Create `code/audit/src/main/scala/clad/audit/ChainAnchor.scala`:

```scala
package clad.audit

import java.nio.file.{Files, Path, StandardOpenOption}
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import scala.util.Try
import upickle.default.*

/** The chain's length and head digest, recorded outside the chain.
  *
  * AuditVerifier.verify can only check the records it is handed, so a chain
  * whose tail has been deleted verifies cleanly: every surviving link matches
  * its predecessor and every surviving signature is valid. The anchor is what
  * makes the absence visible.
  */
case class ChainAnchor(count: Int, headDigest: String, updatedAt: Instant):
  def canonical: String = s"$count|$headDigest|$updatedAt"

case class SignedChainAnchor(anchor: ChainAnchor, signature: Signature)

object ChainAnchor:
  val Genesis: String = "sha256:genesis"

  def forChain(records: Vector[SignedAuditRecord], now: Instant): ChainAnchor =
    ChainAnchor(
      count = records.size,
      headDigest = records.lastOption.map(_.record.digest).getOrElse(Genesis),
      updatedAt = now
    )

  def sign(anchor: ChainAnchor, kms: KeyManagementService): Either[KmsError, SignedChainAnchor] =
    kms.sign(anchor.canonical.getBytes("UTF-8")).map(SignedChainAnchor(anchor, _))

  def isAuthentic(signed: SignedChainAnchor, kms: KeyManagementService): Boolean =
    kms.verify(signed.anchor.canonical.getBytes("UTF-8"), signed.signature) match
      case Right(ok) => ok
      case Left(_)   => false

class ChainAnchorStore(directory: Path):
  private val anchorFile = directory.resolve("anchor.json")

  private given ReadWriter[Instant] = readwriter[String].bimap(_.toString, Instant.parse(_))

  private given ReadWriter[SignedChainAnchor] = readwriter[ujson.Value].bimap(
    s => ujson.Obj(
      "count" -> s.anchor.count,
      "headDigest" -> s.anchor.headDigest,
      "updatedAt" -> writeJs(s.anchor.updatedAt),
      "signature" -> ujson.Obj(
        "value" -> Base64.getEncoder.encodeToString(s.signature.value),
        "keyId" -> s.signature.keyId,
        "algorithm" -> s.signature.algorithm
      )
    ),
    json => SignedChainAnchor(
      ChainAnchor(json("count").num.toInt, json("headDigest").str, read[Instant](json("updatedAt"))),
      Signature(
        Base64.getDecoder.decode(json("signature")("value").str),
        json("signature")("keyId").str,
        json("signature")("algorithm").str
      )
    )
  )

  def write(signed: SignedChainAnchor): Try[Unit] = Try {
    if !Files.exists(directory) then Files.createDirectories(directory)
    Files.write(
      anchorFile,
      upickle.default.write(signed).getBytes("UTF-8"),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    ()
  }

  def read: Try[Option[SignedChainAnchor]] = Try {
    if !Files.exists(anchorFile) then None
    else Some(upickle.default.read[SignedChainAnchor](Files.readString(anchorFile)))
  }
```

- [ ] **Step 4: Update the anchor on every append**

`AppendOnlyFileStore` needs the KMS to sign the anchor. Change its constructor and `append`:

```scala
class AppendOnlyFileStore[F[_]](directory: Path, kms: Option[KeyManagementService] = None)(using lift: Lift[F]) extends AuditStore[F]:

  private val anchorStore = ChainAnchorStore(directory)
```

and, inside `append`, after `appendUnderFileLock(line)` and still holding `writeLock`:

```scala
      writeLock.lock()
      try
        appendUnderFileLock(line)
        kms.foreach { k =>
          val all = Files.readAllLines(chainFile).asScala.toVector.filter(_.nonEmpty)
          val decoded = all.flatMap(l => AuditRecordCodec.decode(l).toOption)
          val anchor = ChainAnchor.forChain(decoded, java.time.Instant.now())
          ChainAnchor.sign(anchor, k).foreach(anchorStore.write(_).get)
        }
      finally writeLock.unlock()
```

`kms` is optional so existing call sites that construct `AppendOnlyFileStore[Try](dir)` keep compiling; those stores write no anchor and `verifyAgainstAnchor` reports `AnchorMissing` for them, which is the correct verdict.

`ChainAnchorSpec` already constructs the store as `AppendOnlyFileStore[Try](dir, Some(kms))`, so no test change is needed here.

- [ ] **Step 5: Add the anchor checks to the verifier**

In `AuditVerifier.scala`, add after `MissingRecordedDigest`:

```scala
  case class TruncationDetected(expectedCount: Int, actualCount: Int) extends VerificationFailure
  case class AnchorHeadMismatch(expected: String, actual: String) extends VerificationFailure
  case object AnchorSignatureInvalid extends VerificationFailure
  case object AnchorMissing extends VerificationFailure
```

and add the new entry point:

```scala
    /** verify, plus the checks that require knowing what the chain should contain.
      *
      * verify alone cannot detect deletion: a truncated chain is internally
      * consistent. The anchor supplies the expected length and head digest.
      */
    def verifyAgainstAnchor(
      records: Vector[SignedAuditRecord],
      anchor: Option[SignedChainAnchor],
      kms: KeyManagementService
    ): VerificationReport =
      val base = verify(records, kms)
      val extra = Vector.newBuilder[VerificationFailure]

      anchor match
        case None =>
          extra += AnchorMissing
        case Some(signed) =>
          if !ChainAnchor.isAuthentic(signed, kms) then extra += AnchorSignatureInvalid
          if signed.anchor.count != records.size then
            extra += TruncationDetected(signed.anchor.count, records.size)
          val actualHead = records.lastOption.map(_.record.digest).getOrElse(ChainAnchor.Genesis)
          if signed.anchor.headDigest != actualHead then
            extra += AnchorHeadMismatch(signed.anchor.headDigest, actualHead)

      val added = extra.result()
      if added.isEmpty then base
      else base.copy(
        failures = base.failures ++ added,
        firstFailureAt = base.firstFailureAt.orElse(Some(records.size))
      )
```

`VerificationReport.isFullyVerified` already requires `failures.isEmpty`, so no change is needed there.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `sbt "audit-test/testOnly clad.audit.ChainAnchorSpec"`
Expected: PASS, all three tests. In particular the second test asserts that plain `verify` still returns `isFullyVerified = true` on the truncated chain — that assertion documents the gap and must stay.

Run: `sbt test`
Expected: PASS. `AppendOnlyFileStore` gained a defaulted parameter, so existing call sites compile unchanged.

- [ ] **Step 7: Commit**

```bash
git add code/audit code/audit-test
git commit -m "feat(audit): anchor the chain so truncation is detectable (AI5)

AuditVerifier.verify checks only the vector it is handed. Deleting the trailing
lines of chain.jsonl leaves a shorter chain in which every link still matches
its predecessor and every signature still verifies, and isFullyVerified
returned true. No checkpoint or watermark existed anywhere in the repo, and no
test exercised truncation.

A signed ChainAnchor now records the chain's length and head digest, written
under the same lock as the append. verifyAgainstAnchor reports
TruncationDetected, AnchorHeadMismatch, AnchorSignatureInvalid or AnchorMissing.

Scope: the anchor is signed, so forging it needs the KMS key, and it cannot
drift from the chain. An attacker holding the key can still rewrite both. This
raises the cost of truncation; it does not make the chain unforgeable.

Implements AI5 (Chain Truncation Detection) from the meta-framework."
```

---

## What this plan does not do

Deliberately out of scope, carried by later plans in the sequence:

- The Lean model gains no length or prefix notion — Plan 4, item 3's neighbour. Until then Lean proves eight theorems about a chain supplied as an argument, and the anchor has no counterpart in the model.
- The differential test still cancels rather than runs — Plan 2, items 2 and 3.
- `GhostDetector.detectFromStores` still hardcodes empty sets — Plan 4, item 7. Task 3 here does not touch ghost detection.
- No conformance annotations are added; `@Conforms` does not exist yet — Plan 3. When it does, `ChainAnchor` is annotated `@Conforms("AI-5")` and `AuditVerifier` `@Conforms("THM-3a", "AI-2", "AI-3")`.
