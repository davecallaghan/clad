package clad.integrity

import clad.audit.{AuditStore, SignedAuditRecord, Lift}
import java.time.Instant
import scala.util.Try

object GhostDetector:

  case class TimeWindow(start: Instant, end: Instant)

  case class GhostReport(
    window: TimeWindow,
    gilEntryCount: Int,
    auditedCount: Int,
    ghostCount: Int,
    ghosts: Vector[Ghost]
  ):
    def hasGhosts: Boolean = ghosts.nonEmpty
    def ghostRate: Double = if gilEntryCount == 0 then 0.0 else ghostCount.toDouble / gilEntryCount

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
    degradedIds: Set[InteractionId]
  ): GhostReport =
    val ghosts = gilEntries.filterNot(e => auditedIds.contains(e.interactionId)).map { entry =>
      val classification =
        if degradedIds.contains(entry.interactionId) then GhostClassification.ComponentFailure
        else GhostClassification.Unknown
      Ghost(entry.interactionId, entry.registeredAt, classification)
    }
    val window = if gilEntries.isEmpty then TimeWindow(Instant.EPOCH, Instant.EPOCH)
      else TimeWindow(gilEntries.head.registeredAt, gilEntries.last.registeredAt)
    GhostReport(window, gilEntries.size, gilEntries.size - ghosts.size, ghosts.size, ghosts)

  def detectFromStores[F[_]](
    gil: InteractionLog[F],
    auditStore: AuditStore[F],
    window: TimeWindow
  )(using lift: Lift[F]): F[GhostReport] =
    lift.fromTry(Try {
      val gilEntries = gil.entriesBetween(window.start, window.end) match
        case t: Try[?] => t.asInstanceOf[Try[Vector[GilEntry]]].get
        case other => throw RuntimeException("Unsupported effect type")
      val auditRecords = auditStore.readAll match
        case t: Try[?] => t.asInstanceOf[Try[Vector[SignedAuditRecord]]].get
        case other => throw RuntimeException("Unsupported effect type")
      val auditedIds = Set.empty[InteractionId]
      val degradedIds = Set.empty[InteractionId]
      detect(gilEntries, auditedIds, degradedIds)
    })
