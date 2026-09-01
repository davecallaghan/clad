package clad.integrity

import clad.audit.{AuditStore, SignedAuditRecord, Lift}
import java.time.Instant
import scala.util.Try

object GhostDetector:

  case class TimeWindow(start: Instant, end: Instant)

  /** @param unlinkableRecordCount
    *   audit records carrying no interaction identifier. They cannot be matched to a
    *   GIL entry, so every interaction they cover looks unaudited. Reported rather
    *   than folded into the ghost count: an interaction whose governance evidence
    *   cannot be located is a different finding from one that has none, and a
    *   detector that conflated them would report the same number either way.
    */
  case class GhostReport(
    window: TimeWindow,
    gilEntryCount: Int,
    auditedCount: Int,
    ghostCount: Int,
    ghosts: Vector[Ghost],
    unlinkableRecordCount: Int = 0
  ):
    def hasGhosts: Boolean = ghosts.nonEmpty
    def ghostRate: Double = if gilEntryCount == 0 then 0.0 else ghostCount.toDouble / gilEntryCount
    /** True when some audit records could not be linked, so `ghostCount` is an upper
      * bound on the true number of ghosts rather than a measurement of it. */
    def isDegradedMeasurement: Boolean = unlinkableRecordCount > 0

  case class Ghost(
    interactionId: InteractionId,
    registeredAt: Instant,
    classification: GhostClassification
  )

  enum GhostClassification:
    case Unknown
    case ComponentFailure
    case EnforcementBypass
    case InFlight

  def detect(
    gilEntries: Vector[GilEntry],
    auditedIds: Set[InteractionId],
    degradedIds: Set[InteractionId],
    unlinkableRecordCount: Int = 0
  ): GhostReport =
    val ghosts = gilEntries.filterNot(e => auditedIds.contains(e.interactionId)).map { entry =>
      val classification =
        if degradedIds.contains(entry.interactionId) then GhostClassification.ComponentFailure
        else GhostClassification.Unknown
      Ghost(entry.interactionId, entry.registeredAt, classification)
    }
    val window = if gilEntries.isEmpty then TimeWindow(Instant.EPOCH, Instant.EPOCH)
      else TimeWindow(gilEntries.head.registeredAt, gilEntries.last.registeredAt)
    GhostReport(window, gilEntries.size, gilEntries.size - ghosts.size, ghosts.size, ghosts,
      unlinkableRecordCount)

  /** Detect ghosts against live stores.
    *
    * @param degradedRecords
    *   supervisor-signed degraded records for the window. A ghost with a degraded
    *   record is a component that failed and said so, which is a governed outcome;
    *   one without is an interaction that reached a model with nothing recording it.
    *   Passing none is honest — it classifies every ghost as `Unknown` — but it
    *   cannot distinguish the two, so callers holding degraded records should pass
    *   them.
    */
  def detectFromStores[F[_]](
    gil: InteractionLog[F],
    auditStore: AuditStore[F],
    window: TimeWindow,
    degradedRecords: Vector[SignedDegradedRecord] = Vector.empty
  )(using lift: Lift[F]): F[GhostReport] =
    lift.fromTry(Try {
      val gilEntries = gil.entriesBetween(window.start, window.end) match
        case t: Try[?] => t.asInstanceOf[Try[Vector[GilEntry]]].get
        case other => throw RuntimeException("Unsupported effect type")
      val auditRecords = auditStore.readAll match
        case t: Try[?] => t.asInstanceOf[Try[Vector[SignedAuditRecord]]].get
        case other => throw RuntimeException("Unsupported effect type")

      // Both sets were hardcoded to empty, which made every GIL entry a ghost: the
      // detector reported total governance failure on a healthy system, and would
      // have reported the same on a failing one. The audit record had no interaction
      // identifier to key on, so this could not be written until AuditRecord carried
      // one (`A_g(i, t)` — Audit Linkability).
      val auditedIds = auditRecords.flatMap(_.record.interactionId).toSet
      val unlinkable = auditRecords.count(_.record.interactionId.isEmpty)
      val degradedIds = degradedRecords.map(_.record.interactionId).toSet

      detect(gilEntries, auditedIds, degradedIds, unlinkable)
    })
