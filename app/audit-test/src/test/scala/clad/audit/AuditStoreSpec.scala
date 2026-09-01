package clad.audit

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import clad.core.*
import clad.evaluation.*
import clad.runtime.*
import clad.audit.test.{InMemoryKeyManagement, InMemoryAuditStore}
import java.time.Instant
import scala.util.Success

class AuditStoreSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:

  val kms: KeyManagementService = InMemoryKeyManagement.default
  val store = InMemoryAuditStore()
  val now: Instant = Instant.parse("2026-04-22T18:00:00Z")
  val later: Instant = Instant.parse("2026-04-22T19:00:00Z")
  val phi: PropertyId = PropertyId.unsafe("phi_access_logging")

  override def beforeEach(): Unit = store.clear()

  def mkSignedRecord(ts: Instant = now): SignedAuditRecord =
    val entry = AuditEntry(
      Constraint.Obligation(phi, Level.Enterprise), "1.0",
      EvaluabilityClass.Mechanical, true, MechanicalDetail(true), ts
    )
    val record = AuditRecord("sha256:abc", Vector(entry), "sha256:cfg", ts, None)
    SignedAuditRecord.sign(record, kms).toOption.get

  "AuditStore.append + readAll" should "store and retrieve records in order" in {
    val r1 = mkSignedRecord(now)
    val r2 = mkSignedRecord(later)
    store.append(r1) shouldBe a[Success[?]]
    store.append(r2) shouldBe a[Success[?]]
    val Success(all) = store.readAll: @unchecked
    all should have size 2
    all.head.record.timestamp shouldBe now
    all.last.record.timestamp shouldBe later
  }

  "AuditStore.count" should "return 0 for empty store" in {
    store.count shouldBe Success(0)
  }

  it should "return correct count after appends" in {
    store.append(mkSignedRecord())
    store.append(mkSignedRecord())
    store.count shouldBe Success(2)
  }

  "AuditStore.readSince" should "filter by timestamp" in {
    store.append(mkSignedRecord(now))
    store.append(mkSignedRecord(later))
    val Success(since) = store.readSince(later): @unchecked
    since should have size 1
    since.head.record.timestamp shouldBe later
  }

  it should "return all records when timestamp is before all" in {
    store.append(mkSignedRecord(now))
    store.append(mkSignedRecord(later))
    val Success(since) = store.readSince(Instant.parse("2026-04-22T17:00:00Z")): @unchecked
    since should have size 2
  }

  it should "return empty when timestamp is after all" in {
    store.append(mkSignedRecord(now))
    val Success(since) = store.readSince(Instant.parse("2026-04-23T00:00:00Z")): @unchecked
    since shouldBe empty
  }

  "AuditStore" should "have no update or delete methods" in {
    val methods = classOf[AuditStore[?]].getMethods.map(_.getName).toSet
    methods should not contain "update"
    methods should not contain "delete"
    methods should not contain "remove"
    methods should not contain "replace"
  }
