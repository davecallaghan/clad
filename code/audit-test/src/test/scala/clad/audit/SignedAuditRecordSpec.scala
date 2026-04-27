package clad.audit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.InMemoryKeyManagement
import java.time.Instant

class SignedAuditRecordSpec extends AnyFlatSpec with Matchers:
  val testKey: Array[Byte] = "test-secret-key-32bytes!12345678".getBytes("UTF-8")
  val kms: KeyManagementService = InMemoryKeyManagement(testKey, "test-key-1")
  val now: Instant = Instant.parse("2026-04-22T18:00:00Z")
  val phi: PropertyId = PropertyId.unsafe("phi_access_logging")

  def mkRecord(): AuditRecord =
    val entry = AuditEntry(
      Constraint.Obligation(phi, Level.Enterprise), "1.0",
      EvaluabilityClass.Mechanical, true, MechanicalDetail(true), now
    )
    AuditRecord("sha256:abc", Vector(entry), "sha256:cfg", now, None)

  "HmacKeyManagement" should "expose keyId" in {
    kms.keyId shouldBe "test-key-1"
  }

  it should "produce signature with correct keyId and algorithm HmacSHA256" in {
    val data = "test data".getBytes("UTF-8")
    val result = kms.sign(data)

    result.isRight shouldBe true
    val signature = result.toOption.get
    signature.keyId shouldBe "test-key-1"
    signature.algorithm shouldBe "HmacSHA256"
    signature.value.length should be > 0
  }

  it should "be deterministic (same data = same signature)" in {
    val data = "test data".getBytes("UTF-8")
    val sig1 = kms.sign(data).toOption.get
    val sig2 = kms.sign(data).toOption.get

    sig1.value shouldBe sig2.value
  }

  it should "produce different signatures for different data" in {
    val data1 = "test data 1".getBytes("UTF-8")
    val data2 = "test data 2".getBytes("UTF-8")
    val sig1 = kms.sign(data1).toOption.get
    val sig2 = kms.sign(data2).toOption.get

    sig1.value should not be sig2.value
  }

  it should "verify returns true for valid signature" in {
    val data = "test data".getBytes("UTF-8")
    val signature = kms.sign(data).toOption.get
    val result = kms.verify(data, signature)

    result shouldBe Right(true)
  }

  it should "verify returns false for tampered signature bytes" in {
    val data = "test data".getBytes("UTF-8")
    val signature = kms.sign(data).toOption.get
    val tamperedSignature = signature.copy(value = signature.value.reverse)
    val result = kms.verify(data, tamperedSignature)

    result shouldBe Right(false)
  }

  it should "verify returns false for wrong data" in {
    val data1 = "test data 1".getBytes("UTF-8")
    val data2 = "test data 2".getBytes("UTF-8")
    val signature = kms.sign(data1).toOption.get
    val result = kms.verify(data2, signature)

    result shouldBe Right(false)
  }

  it should "produce different signatures with different keys" in {
    val otherKey = "other-secret-key-32bytes!12345678".getBytes("UTF-8")
    val kms2 = InMemoryKeyManagement(otherKey, "test-key-2")
    val data = "test data".getBytes("UTF-8")

    val sig1 = kms.sign(data).toOption.get
    val sig2 = kms2.sign(data).toOption.get

    sig1.value should not be sig2.value
  }

  it should "fail cross-KMS verification (sign with key1, verify with key2)" in {
    val otherKey = "other-secret-key-32bytes!12345678".getBytes("UTF-8")
    val kms2 = InMemoryKeyManagement(otherKey, "test-key-2")
    val data = "test data".getBytes("UTF-8")

    val signature = kms.sign(data).toOption.get
    val result = kms2.verify(data, signature)

    result shouldBe Right(false)
  }

  "SignedAuditRecord.sign" should "produce a SignedAuditRecord with correct signature metadata" in {
    val record = mkRecord()
    val result = SignedAuditRecord.sign(record, kms)

    result.isRight shouldBe true
    val signedRecord = result.toOption.get
    signedRecord.record shouldBe record
    signedRecord.signature.keyId shouldBe "test-key-1"
    signedRecord.signature.algorithm shouldBe "HmacSHA256"
  }

  "SignedAuditRecord.isAuthentic" should "return true for properly signed record" in {
    val record = mkRecord()
    val signedRecord = SignedAuditRecord.sign(record, kms).toOption.get
    val result = signedRecord.isAuthentic(kms)

    result shouldBe Right(true)
  }

  it should "return false with wrong key" in {
    val record = mkRecord()
    val signedRecord = SignedAuditRecord.sign(record, kms).toOption.get

    val otherKey = "other-secret-key-32bytes!12345678".getBytes("UTF-8")
    val kms2 = InMemoryKeyManagement(otherKey, "test-key-2")
    val result = signedRecord.isAuthentic(kms2)

    result shouldBe Right(false)
  }

  it should "be deterministic (signing same record twice produces same signature)" in {
    val record = mkRecord()
    val signed1 = SignedAuditRecord.sign(record, kms).toOption.get
    val signed2 = SignedAuditRecord.sign(record, kms).toOption.get

    signed1.signature.value shouldBe signed2.signature.value
  }
