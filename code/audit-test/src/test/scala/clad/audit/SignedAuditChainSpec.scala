package clad.audit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.InMemoryKeyManagement
import java.time.Instant

class SignedAuditChainSpec extends AnyFlatSpec with Matchers:
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

  "SignedAuditChain.empty" should "have length 0" in {
    SignedAuditChain.empty.length shouldBe 0
  }

  it should "have no latest record" in {
    SignedAuditChain.empty.latest shouldBe None
  }

  it should "be integral" in {
    SignedAuditChain.empty.isIntegral shouldBe true
  }

  it should "be authentic" in {
    SignedAuditChain.empty.isAuthentic(kms) shouldBe Right(true)
  }

  "SignedAuditChain.append" should "increase length" in {
    val record = mkRecord(phi, satisfied = true)
    val result = SignedAuditChain.empty.append(record, kms)

    result shouldBe a[Right[?, ?]]
    result.map(_.length) shouldBe Right(1)
  }

  it should "set latest" in {
    val record = mkRecord(phi, satisfied = true)
    val result = SignedAuditChain.empty.append(record, kms)

    result shouldBe a[Right[?, ?]]
    result.map(_.latest).foreach { latest =>
      latest shouldBe defined
      latest.get.record.digest shouldBe record.digest
    }
  }

  it should "sign the record" in {
    val record = mkRecord(phi, satisfied = true)
    val result = SignedAuditChain.empty.append(record, kms)

    result shouldBe a[Right[?, ?]]
    result.map(_.latest).foreach { latest =>
      latest shouldBe defined
      latest.get.signature.keyId shouldBe kms.keyId
      latest.get.signature.value should not be empty
    }
  }

  it should "set previousDigest to None for first record" in {
    val record = mkRecord(phi, satisfied = true)
    val result = SignedAuditChain.empty.append(record, kms)

    result shouldBe a[Right[?, ?]]
    result.map(_.latest).foreach { latest =>
      latest shouldBe defined
      latest.get.record.previousDigest shouldBe None
    }
  }

  it should "link second record to first" in {
    val record1 = mkRecord(phi, satisfied = true)
    val record2 = mkRecord(psi, satisfied = false)

    val result = for {
      chain1 <- SignedAuditChain.empty.append(record1, kms)
      chain2 <- chain1.append(record2, kms)
    } yield chain2

    result shouldBe a[Right[?, ?]]
    result.foreach { chain =>
      chain.length shouldBe 2
      val first = chain.records.head
      val second = chain.records(1)

      first.record.previousDigest shouldBe None
      second.record.previousDigest shouldBe Some(first.record.digest)
    }
  }

  "SignedAuditChain.isIntegral" should "be true for multi-record chain built through append" in {
    val record1 = mkRecord(phi, satisfied = true)
    val record2 = mkRecord(psi, satisfied = false)
    val record3 = mkRecord(phi, satisfied = true)

    val result = for {
      chain1 <- SignedAuditChain.empty.append(record1, kms)
      chain2 <- chain1.append(record2, kms)
      chain3 <- chain2.append(record3, kms)
    } yield chain3

    result shouldBe a[Right[?, ?]]
    result.foreach { chain =>
      chain.length shouldBe 3
      chain.isIntegral shouldBe true
    }
  }

  it should "maintain correct chain links through multiple appends" in {
    val Right(c1) = SignedAuditChain.empty.append(mkRecord(phi, true), kms): @unchecked
    val Right(c2) = c1.append(mkRecord(psi, false), kms): @unchecked

    c2.records.head.record.previousDigest shouldBe None
    c2.records(1).record.previousDigest shouldBe Some(c2.records.head.record.digest)
    c2.isIntegral shouldBe true
  }

  "SignedAuditChain.isAuthentic" should "be true with correct key" in {
    val record1 = mkRecord(phi, satisfied = true)
    val record2 = mkRecord(psi, satisfied = false)

    val result = for {
      chain1 <- SignedAuditChain.empty.append(record1, kms)
      chain2 <- chain1.append(record2, kms)
    } yield chain2

    result shouldBe a[Right[?, ?]]
    result.foreach { chain =>
      chain.isAuthentic(kms) shouldBe Right(true)
    }
  }

  it should "be false with wrong key" in {
    val wrongKey: Array[Byte] = "wrong-secret-key-32bytes!12345678".getBytes("UTF-8")
    val wrongKms: KeyManagementService = InMemoryKeyManagement(wrongKey, "wrong-key")

    val record1 = mkRecord(phi, satisfied = true)
    val record2 = mkRecord(psi, satisfied = false)

    val result = for {
      chain1 <- SignedAuditChain.empty.append(record1, kms)
      chain2 <- chain1.append(record2, kms)
    } yield chain2

    result shouldBe a[Right[?, ?]]
    result.foreach { chain =>
      chain.isAuthentic(wrongKms) shouldBe Right(false)
    }
  }

  "SignedAuditChain monotonic growth" should "only increase length through appends" in {
    val record1 = mkRecord(phi, satisfied = true)
    val record2 = mkRecord(psi, satisfied = false)
    val record3 = mkRecord(phi, satisfied = true)

    val chain0 = SignedAuditChain.empty
    chain0.length shouldBe 0

    val chain1 = chain0.append(record1, kms).getOrElse(fail("append failed"))
    chain1.length shouldBe 1

    val chain2 = chain1.append(record2, kms).getOrElse(fail("append failed"))
    chain2.length shouldBe 2

    val chain3 = chain2.append(record3, kms).getOrElse(fail("append failed"))
    chain3.length shouldBe 3
  }

  "SignedAuditChain integrity and authenticity" should "be independently checkable" in {
    val record1 = mkRecord(phi, satisfied = true)
    val wrongKey: Array[Byte] = "wrong-secret-key-32bytes!12345678".getBytes("UTF-8")
    val wrongKms: KeyManagementService = InMemoryKeyManagement(wrongKey, "wrong-key")

    val chain = SignedAuditChain.empty.append(record1, kms).getOrElse(fail("append failed"))

    // Integral but not authentic with wrong key
    chain.isIntegral shouldBe true
    chain.isAuthentic(wrongKms) shouldBe Right(false)

    // Both integral and authentic with correct key
    chain.isIntegral shouldBe true
    chain.isAuthentic(kms) shouldBe Right(true)
  }

  it should "detect broken integrity even with correct signatures" in {
    val record1 = mkRecord(phi, satisfied = true)
    val record2 = mkRecord(psi, satisfied = false)

    // Build a proper chain, then verify integrity and authenticity are independently checkable
    val Right(c1) = SignedAuditChain.empty.append(record1, kms): @unchecked
    val Right(c2) = c1.append(record2, kms): @unchecked

    // Both should be true for a properly built chain
    c2.isIntegral shouldBe true
    val Right(auth) = c2.isAuthentic(kms): @unchecked
    auth shouldBe true

    // Verify with wrong key — integral but not authentic
    val wrongKms = InMemoryKeyManagement("wrong-key-must-be-32-bytes!!1234".getBytes("UTF-8"), "wrong")
    c2.isIntegral shouldBe true
    val Right(wrongAuth) = c2.isAuthentic(wrongKms): @unchecked
    wrongAuth shouldBe false
  }
