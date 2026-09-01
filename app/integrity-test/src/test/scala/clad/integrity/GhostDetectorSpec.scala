package clad.integrity

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant
import scala.util.Success
import clad.audit.{SignedAuditRecord, Signature}
import clad.audit.test.InMemoryAuditStore
import clad.core.{Constraint, Level, PropertyId}
import clad.evaluation.MechanicalDetail
import clad.integrity.test.InMemoryInteractionLog
import clad.runtime.{AuditEntry, AuditRecord, EvaluabilityClass}

class GhostDetectorSpec extends AnyFlatSpec with Matchers:
  import GhostDetector.*

  val now: Instant = Instant.parse("2026-04-23T10:00:00Z")
  val later: Instant = Instant.parse("2026-04-23T11:00:00Z")

  def mkEntry(id: String, ts: Instant = now): GilEntry = GilEntry(InteractionId(id), ts)

  "GhostDetector.detect" should "return empty report for empty GIL entries" in {
    val report = GhostDetector.detect(Vector.empty, Set.empty, Set.empty)

    report.gilEntryCount shouldBe 0
    report.ghostCount shouldBe 0
    report.hasGhosts shouldBe false
    report.ghostRate shouldBe 0.0
  }

  it should "return no ghosts when all entries are audited" in {
    val entries = Vector(mkEntry("id1"), mkEntry("id2"), mkEntry("id3"))
    val auditedIds = Set(InteractionId("id1"), InteractionId("id2"), InteractionId("id3"))

    val report = GhostDetector.detect(entries, auditedIds, Set.empty)

    report.gilEntryCount shouldBe 3
    report.auditedCount shouldBe 3
    report.ghostCount shouldBe 0
    report.hasGhosts shouldBe false
  }

  it should "detect ghosts with Unknown classification for unaudited entries" in {
    val entries = Vector(mkEntry("id1"), mkEntry("id2"), mkEntry("id3"))
    val auditedIds = Set(InteractionId("id1"))

    val report = GhostDetector.detect(entries, auditedIds, Set.empty)

    report.gilEntryCount shouldBe 3
    report.auditedCount shouldBe 1
    report.ghostCount shouldBe 2
    report.hasGhosts shouldBe true
    report.ghosts.size shouldBe 2
    report.ghosts.foreach { ghost =>
      ghost.classification shouldBe GhostClassification.Unknown
    }
  }

  it should "classify ghosts as ComponentFailure when in degradedIds" in {
    val entries = Vector(mkEntry("id1"), mkEntry("id2"))
    val degradedIds = Set(InteractionId("id2"))

    val report = GhostDetector.detect(entries, Set.empty, degradedIds)

    report.ghostCount shouldBe 2
    val ghost2 = report.ghosts.find(_.interactionId == InteractionId("id2")).get
    ghost2.classification shouldBe GhostClassification.ComponentFailure
  }

  it should "handle mix of audited, degraded, and unknown entries" in {
    val entries = Vector(
      mkEntry("id1"),
      mkEntry("id2"),
      mkEntry("id3"),
      mkEntry("id4")
    )
    val auditedIds = Set(InteractionId("id1"))
    val degradedIds = Set(InteractionId("id3"))

    val report = GhostDetector.detect(entries, auditedIds, degradedIds)

    report.gilEntryCount shouldBe 4
    report.auditedCount shouldBe 1
    report.ghostCount shouldBe 3
    report.hasGhosts shouldBe true

    val ghostMap = report.ghosts.map(g => g.interactionId -> g.classification).toMap
    ghostMap(InteractionId("id2")) shouldBe GhostClassification.Unknown
    ghostMap(InteractionId("id3")) shouldBe GhostClassification.ComponentFailure
    ghostMap(InteractionId("id4")) shouldBe GhostClassification.Unknown
  }

  it should "calculate ghostRate correctly" in {
    val entries = Vector(
      mkEntry("id1"),
      mkEntry("id2"),
      mkEntry("id3"),
      mkEntry("id4")
    )
    val auditedIds = Set(InteractionId("id1"))

    val report = GhostDetector.detect(entries, auditedIds, Set.empty)

    report.ghostRate shouldBe 0.75
  }

  "GhostClassification" should "have 4 values" in {
    import GhostClassification.*
    val values = Set(Unknown, ComponentFailure, EnforcementBypass, InFlight)
    values.size shouldBe 4
  }

    // --- detectFromStores: deriving the sets from real stores ---
    //
    // These are regression tests. detectFromStores hardcoded auditedIds and
    // degradedIds to Set.empty, so it reported every GIL entry as a ghost —
    // total governance failure on a healthy system, and the identical report
    // on a failing one.

    def mkAuditRecord(interactionId: Option[InteractionId], ts: Instant = now): SignedAuditRecord =
      val entry = AuditEntry(
        Constraint.Obligation(PropertyId.unsafe("phi_logging"), Level.Enterprise), "1.0",
        EvaluabilityClass.Mechanical, satisfied = true, MechanicalDetail(true), ts
      )
      val record = AuditRecord("sha256:artifact", Vector(entry), "sha256:config", ts, None, interactionId)
      SignedAuditRecord(record, Signature(Array[Byte](1, 2, 3), "key-1", "test"), Some(record.digest))

    def storesWith(
      gilIds: Vector[String],
      auditIds: Vector[Option[String]]
    ): (InMemoryInteractionLog, InMemoryAuditStore) =
      val gil = new InMemoryInteractionLog
      gilIds.foreach(id => gil.register(mkEntry(id)) shouldBe a[Success[?]])
      val store = new InMemoryAuditStore
      auditIds.foreach(id => store.append(mkAuditRecord(id.map(InteractionId(_)))) shouldBe a[Success[?]])
      (gil, store)

    val window: TimeWindow = TimeWindow(now.minusSeconds(60), later)

    "GhostDetector.detectFromStores" should "find no ghosts when every entry has a linked audit record" in {
      val (gil, store) = storesWith(Vector("id1", "id2"), Vector(Some("id1"), Some("id2")))

      val Success(report) = GhostDetector.detectFromStores(gil, store, window): @unchecked

      report.gilEntryCount shouldBe 2
      report.ghostCount shouldBe 0
      report.hasGhosts shouldBe false
      report.unlinkableRecordCount shouldBe 0
      report.isDegradedMeasurement shouldBe false
    }

    it should "report only the entries that genuinely lack an audit record" in {
      val (gil, store) = storesWith(Vector("id1", "id2", "id3"), Vector(Some("id1")))

      val Success(report) = GhostDetector.detectFromStores(gil, store, window): @unchecked

      report.ghostCount shouldBe 2
      report.ghosts.map(_.interactionId).toSet shouldBe Set(InteractionId("id2"), InteractionId("id3"))
      report.ghosts.map(_.classification).toSet shouldBe Set(GhostClassification.Unknown)
    }

    it should "count records with no identifier as unlinkable rather than silently as ghosts" in {
      val (gil, store) = storesWith(Vector("id1", "id2"), Vector(None, None))

      val Success(report) = GhostDetector.detectFromStores(gil, store, window): @unchecked

      // Both entries still look unaudited — that part is unavoidable — but the report
      // says why, so the number is legible as an upper bound rather than a measurement.
      report.ghostCount shouldBe 2
      report.unlinkableRecordCount shouldBe 2
      report.isDegradedMeasurement shouldBe true
    }

    it should "classify a ghost with a degraded record as ComponentFailure" in {
      val (gil, store) = storesWith(Vector("id1", "id2"), Vector(Some("id1")))
      val degraded = SignedDegradedRecord(
        DegradedAuditRecord(
          InteractionId("id2"), now, ComponentId("roc"), DegradedStatus.Degraded,
          "classifier timeout", FailurePosture.FailClosed, FailureAction.Blocked
        ),
        Signature(Array[Byte](9), "key-1", "test"),
        signedBy = ComponentId("supervisor"),
        onBehalfOf = ComponentId("roc")
      )

      val Success(report) =
        GhostDetector.detectFromStores(gil, store, window, Vector(degraded)): @unchecked

      report.ghostCount shouldBe 1
      report.ghosts.head.interactionId shouldBe InteractionId("id2")
      report.ghosts.head.classification shouldBe GhostClassification.ComponentFailure
    }
