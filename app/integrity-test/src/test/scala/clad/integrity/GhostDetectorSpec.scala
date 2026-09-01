package clad.integrity

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.Instant

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
