package clad.runtime

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import clad.core.*
import clad.evaluation.*
import java.time.Instant

class AuditRecordSpec extends AnyFlatSpec with Matchers:

  val now: Instant = Instant.parse("2026-04-22T18:00:00Z")
  val phi: PropertyId = PropertyId.unsafe("phi_access_logging")
  val psi: PropertyId = PropertyId.unsafe("pii_detected")

  def mkEntry(prop: PropertyId, satisfied: Boolean): AuditEntry =
    AuditEntry(
      Constraint.Obligation(prop, Level.Enterprise), "1.0",
      EvaluabilityClass.Mechanical, satisfied, MechanicalDetail(satisfied), now
    )

  def mkRecord(entries: Vector[AuditEntry], prev: Option[String] = None): AuditRecord =
    AuditRecord("sha256:abc123", entries, "sha256:config456", now, prev)

  "AuditEntry" should "store constraint, version, class, and satisfaction" in {
    val entry = mkEntry(phi, satisfied = true)

    entry.constraint shouldBe Constraint.Obligation(phi, Level.Enterprise)
    entry.constraintVersion shouldBe "1.0"
    entry.evaluabilityClass shouldBe EvaluabilityClass.Mechanical
    entry.satisfied shouldBe true
    entry.detail shouldBe MechanicalDetail(true)
    entry.timestamp shouldBe now
  }

  "AuditRecord.isComplete" should "return true when entries match expected constraints" in {
    val phiEntry = mkEntry(phi, satisfied = true)
    val psiEntry = mkEntry(psi, satisfied = false)
    val record = mkRecord(Vector(phiEntry, psiEntry))

    val expected: Set[Constraint] = Set(
      Constraint.Obligation(phi, Level.Enterprise),
      Constraint.Obligation(psi, Level.Enterprise)
    )

    record.isComplete(expected) shouldBe true
  }

  it should "return false when constraints are missing" in {
    val phiEntry = mkEntry(phi, satisfied = true)
    val record = mkRecord(Vector(phiEntry))

    val expected: Set[Constraint] = Set(
      Constraint.Obligation(phi, Level.Enterprise),
      Constraint.Obligation(psi, Level.Enterprise)
    )

    record.isComplete(expected) shouldBe false
  }

  "AuditRecord.entryCount" should "return the number of entries" in {
    val record1 = mkRecord(Vector.empty)
    val record2 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val record3 = mkRecord(Vector(mkEntry(phi, satisfied = true), mkEntry(psi, satisfied = false)))

    record1.entryCount shouldBe 0
    record2.entryCount shouldBe 1
    record3.entryCount shouldBe 2
  }

  "AuditRecord.satisfiedCount" should "return the number of satisfied entries" in {
    val entries = Vector(
      mkEntry(phi, satisfied = true),
      mkEntry(psi, satisfied = false),
      mkEntry(PropertyId.unsafe("prop3"), satisfied = true)
    )
    val record = mkRecord(entries)

    record.satisfiedCount shouldBe 2
  }

  "AuditRecord.unsatisfiedEntries" should "return only unsatisfied entries" in {
    val phiEntry = mkEntry(phi, satisfied = true)
    val psiEntry = mkEntry(psi, satisfied = false)
    val entries = Vector(phiEntry, psiEntry)
    val record = mkRecord(entries)

    val unsatisfied = record.unsatisfiedEntries
    unsatisfied.size shouldBe 1
    unsatisfied.head shouldBe psiEntry
  }

  "AuditRecord.digest" should "be non-empty" in {
    val record = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    record.digest should not be empty
  }

  it should "start with sha256:" in {
    val record = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    record.digest should startWith("sha256:")
  }

  it should "be deterministic (same record = same digest)" in {
    val entries = Vector(mkEntry(phi, satisfied = true))
    val record1 = mkRecord(entries)
    val record2 = mkRecord(entries)

    record1.digest shouldBe record2.digest
  }

  it should "differ when entries differ" in {
    val record1 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val record2 = mkRecord(Vector(mkEntry(phi, satisfied = false)))

    record1.digest should not be record2.digest
  }

  it should "differ when previousDigest differs" in {
    val entries = Vector(mkEntry(phi, satisfied = true))
    val record1 = mkRecord(entries, prev = None)
    val record2 = mkRecord(entries, prev = Some("sha256:prev123"))

    record1.digest should not be record2.digest
  }

  "AuditChain.empty" should "have length 0" in {
    AuditChain.empty.length shouldBe 0
  }

  it should "have no latest record" in {
    AuditChain.empty.latest shouldBe None
  }

  it should "be integral" in {
    AuditChain.empty.isIntegral shouldBe true
  }

  "AuditChain.append" should "increase length" in {
    val chain = AuditChain.empty
    val record = mkRecord(Vector(mkEntry(phi, satisfied = true)))

    val chain2 = chain.append(record)
    chain2.length shouldBe 1

    val chain3 = chain2.append(record)
    chain3.length shouldBe 2
  }

  it should "set latest record" in {
    val chain = AuditChain.empty
    val record1 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val record2 = mkRecord(Vector(mkEntry(psi, satisfied = false)))

    val chain2 = chain.append(record1)
    chain2.latest.map(_.entries.head.constraint.property) shouldBe Some(phi)

    val chain3 = chain2.append(record2)
    chain3.latest.map(_.entries.head.constraint.property) shouldBe Some(psi)
  }

  it should "link previousDigest to prior digest" in {
    val chain = AuditChain.empty
    val record1 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val record2 = mkRecord(Vector(mkEntry(psi, satisfied = false)))

    val chain2 = chain.append(record1)
    val chain3 = chain2.append(record2)

    val latest = chain3.latest.get
    val previous = chain2.latest.get

    latest.previousDigest shouldBe Some(previous.digest)
  }

  "AuditChain.isIntegral" should "be true for properly linked chain" in {
    val chain = AuditChain.empty
    val record1 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val record2 = mkRecord(Vector(mkEntry(psi, satisfied = false)))
    val record3 = mkRecord(Vector(mkEntry(PropertyId.unsafe("prop3"), satisfied = true)))

    val chain2 = chain.append(record1)
    chain2.isIntegral shouldBe true

    val chain3 = chain2.append(record2)
    chain3.isIntegral shouldBe true

    val chain4 = chain3.append(record3)
    chain4.isIntegral shouldBe true
  }

  it should "maintain integrity through multiple appends" in {
    val r1 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val r2 = mkRecord(Vector(mkEntry(psi, satisfied = false)))
    val r3 = mkRecord(Vector(mkEntry(phi, satisfied = true)))
    val chain = AuditChain.empty.append(r1).append(r2).append(r3)

    chain.isIntegral shouldBe true
    chain.length shouldBe 3
    chain.records(1).previousDigest shouldBe Some(chain.records(0).digest)
    chain.records(2).previousDigest shouldBe Some(chain.records(1).digest)
  }

  "AuditChain monotonic growth" should "only increase length" in {
    val chain = AuditChain.empty
    val record = mkRecord(Vector(mkEntry(phi, satisfied = true)))

    val lengths = (1 to 5).scanLeft(chain)((c, _) => c.append(record)).map(_.length)

    lengths shouldBe Vector(0, 1, 2, 3, 4, 5)
  }
