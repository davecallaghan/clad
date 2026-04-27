package clad.audit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.{InMemoryKeyManagement, InMemoryAuditStore}
import java.time.Instant
import scala.util.Try

class AuditVerifierSpec extends AnyFlatSpec with Matchers:

  val testKey: Array[Byte] = "test-secret-key-32bytes!12345678".getBytes("UTF-8")
  val kms: KeyManagementService = InMemoryKeyManagement(testKey, "test-key-1")
  val now: Instant = Instant.parse("2026-04-22T18:00:00Z")
  val phi: PropertyId = PropertyId.unsafe("phi_access_logging")
  val psi: PropertyId = PropertyId.unsafe("pii_detected")

  def mkRecord(prop: PropertyId, satisfied: Boolean): AuditRecord =
    val entry = AuditEntry(
      Constraint.Obligation(prop, Level.Enterprise), "1.0",
      EvaluabilityClass.Mechanical, satisfied, MechanicalDetail(satisfied), now
    )
    AuditRecord("sha256:abc", Vector(entry), "sha256:cfg", now, None)

  def buildChain(records: AuditRecord*): SignedAuditChain =
    records.foldLeft(SignedAuditChain.empty) { (chain, record) =>
      chain.append(record, kms).toOption.get
    }

  "AuditVerifier" should "verify empty chain as fully verified" in {
    val report = AuditVerifier.verify(Vector.empty, kms)
    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 0
    report.chainIntegral shouldBe true
    report.allSignaturesValid shouldBe true
    report.failures shouldBe empty
    report.firstFailureAt shouldBe None
  }

  it should "verify clean single-record chain" in {
    val record = mkRecord(phi, satisfied = true)
    val chain = buildChain(record)
    val report = AuditVerifier.verify(chain.records, kms)

    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 1
    report.chainIntegral shouldBe true
    report.allSignaturesValid shouldBe true
    report.failures shouldBe empty
    report.firstFailureAt shouldBe None
  }

  it should "verify clean multi-record chain" in {
    val r1 = mkRecord(phi, satisfied = true)
    val r2 = mkRecord(psi, satisfied = false)
    val r3 = mkRecord(phi, satisfied = true)
    val chain = buildChain(r1, r2, r3)
    val report = AuditVerifier.verify(chain.records, kms)

    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 3
    report.chainIntegral shouldBe true
    report.allSignaturesValid shouldBe true
    report.failures shouldBe empty
  }

  it should "detect tampered content via digest mismatch" in {
    val record = mkRecord(phi, satisfied = true)
    val chain = buildChain(record)

    // Tamper with the record's artifactDigest
    val tamperedRecord = chain.records(0).record.copy(artifactDigest = "sha256:tampered")
    val tamperedSigned = chain.records(0).copy(record = tamperedRecord)

    val report = AuditVerifier.verify(Vector(tamperedSigned), kms)

    report.isFullyVerified shouldBe false
    // Tampered content causes the digest to recompute, invalidating the original signature
    report.allSignaturesValid shouldBe false
    report.failures.exists(_.isInstanceOf[AuditVerifier.InvalidSignature]) shouldBe true
    report.firstFailureAt shouldBe Some(0)
  }

  it should "detect broken chain link" in {
    val r1 = mkRecord(phi, satisfied = true)
    val r2 = mkRecord(psi, satisfied = false)
    val chain = buildChain(r1, r2)

    // Re-sign record 1 with wrong previousDigest
    val wrongPrevious = Some("sha256:wrong")
    val brokenRecord = chain.records(1).record.copy(previousDigest = wrongPrevious)
    val brokenSigned = SignedAuditRecord.sign(brokenRecord, kms).toOption.get

    val brokenChain = Vector(chain.records(0), brokenSigned)
    val report = AuditVerifier.verify(brokenChain, kms)

    report.isFullyVerified shouldBe false
    report.chainIntegral shouldBe false
    report.failures should have size 1
    report.failures.head shouldBe a[AuditVerifier.BrokenChainLink]
    val failure = report.failures.head.asInstanceOf[AuditVerifier.BrokenChainLink]
    failure.index shouldBe 1
    failure.expected shouldBe chain.records(0).record.digest
  }

  it should "detect invalid signature with wrong KMS key" in {
    val record = mkRecord(phi, satisfied = true)
    val chain = buildChain(record)

    val wrongKms = InMemoryKeyManagement("wrong-key-must-be-32-bytes!!1234".getBytes("UTF-8"), "wrong")
    val report = AuditVerifier.verify(chain.records, wrongKms)

    report.isFullyVerified shouldBe false
    report.allSignaturesValid shouldBe false
    report.failures should have size 1
    report.failures.head shouldBe a[AuditVerifier.InvalidSignature]
  }

  it should "detect first record with non-None previousDigest" in {
    val record = mkRecord(phi, satisfied = true).copy(previousDigest = Some("sha256:invalid"))
    val signed = SignedAuditRecord.sign(record, kms).toOption.get

    val report = AuditVerifier.verify(Vector(signed), kms)

    report.isFullyVerified shouldBe false
    report.chainIntegral shouldBe false
    report.failures should have size 1
    report.failures.head shouldBe a[AuditVerifier.BrokenChainLink]
    val failure = report.failures.head.asInstanceOf[AuditVerifier.BrokenChainLink]
    failure.index shouldBe 0
    failure.expected shouldBe "None"
  }

  it should "report all failures for wrong key on 3-record chain" in {
    val r1 = mkRecord(phi, satisfied = true)
    val r2 = mkRecord(psi, satisfied = false)
    val r3 = mkRecord(phi, satisfied = true)
    val chain = buildChain(r1, r2, r3)

    val wrongKms = InMemoryKeyManagement("wrong-key-must-be-32-bytes!!1234".getBytes("UTF-8"), "wrong")
    val report = AuditVerifier.verify(chain.records, wrongKms)

    report.isFullyVerified shouldBe false
    report.allSignaturesValid shouldBe false
    report.failures should have size 3
    report.failures.foreach { failure =>
      failure shouldBe a[AuditVerifier.InvalidSignature]
    }
  }

  it should "set firstFailureAt to index 0 for wrong-key verification" in {
    val r1 = mkRecord(phi, satisfied = true)
    val r2 = mkRecord(psi, satisfied = false)
    val r3 = mkRecord(phi, satisfied = true)
    val chain = buildChain(r1, r2, r3)

    val wrongKms = InMemoryKeyManagement("wrong-key-must-be-32-bytes!!1234".getBytes("UTF-8"), "wrong")
    val report = AuditVerifier.verify(chain.records, wrongKms)

    report.firstFailureAt shouldBe Some(0)
  }

  it should "verifyStore with InMemoryAuditStore" in {
    val r1 = mkRecord(phi, satisfied = true)
    val r2 = mkRecord(psi, satisfied = false)
    val chain = buildChain(r1, r2)

    val store = InMemoryAuditStore()
    chain.records.foreach { record =>
      store.append(record)
    }

    val report = AuditVerifier.verifyStore(store, kms).get

    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 2
  }

  it should "verifyStore with empty store" in {
    val store = InMemoryAuditStore()
    val report = AuditVerifier.verifyStore(store, kms).get

    report.isFullyVerified shouldBe true
    report.recordCount shouldBe 0
  }
