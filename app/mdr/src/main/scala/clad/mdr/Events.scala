package clad.mdr

import clad.core.PropertyId
import clad.integrity.{ComponentId, InteractionId}
import clad.monitoring.*
import clad.output.{FallbackAction, PipelineDecision}

/** A snapshot's rows as clad governance events, for the detectors built on clad's monitoring traits. Absence (a ghost,
  * an interaction still in flight) cannot be an event; the ghosts detector reconciles it from the snapshot. */
object Events:
  val Source: ComponentId = ComponentId("mdr")

  private def fallback(outcome: String): FallbackAction = outcome match
    case "fallback:redacted"      => FallbackAction.Redacted
    case "fallback:safe_response" => FallbackAction.SafeResponseSubstituted
    case _                        => FallbackAction.Escalated

  /** Input signals become violations of `input_<category>`: clad property ids allow no colon. */
  def inputProperty(category: String): PropertyId = PropertyId.unsafe(s"input_$category")

  def from(s: Snapshot): Vector[GovernanceEvent] =
    val failures = s.degraded.map { d =>
      val r = d.record
      ComponentFailure(s"degraded:${r.interactionId.value}:${r.component.value}", r.timestamp, Some(r.interactionId), Source,
        r.component, r.postureApplied, r.failureReason)
    }
    val evaluated = s.records.map { r =>
      EvaluationCompleted(s"record:${r.interactionId}:${r.component}:${r.timestamp}", r.timestamp,
        Some(InteractionId(r.interactionId)), Source, r.entries.size, r.entries.count(_.satisfied), r.entries.forall(_.satisfied))
    }
    val blocked = s.records.filter(r => r.component == "roc" && r.outcome.exists(o => o == "block" || o.startsWith("fallback:")))
      .map(r => OutputBlocked(s"blocked:${r.interactionId}:${r.timestamp}", r.timestamp, Some(InteractionId(r.interactionId)),
        Source, PipelineDecision.Block(Vector.empty), fallback(r.outcome.getOrElse(""))))
    val inputs = s.signals.map { (iid, sig, at) =>
      ViolationDetected(s"signal:$iid:${sig.detector}:$at", at, Some(InteractionId(iid)), Source,
        inputProperty(sig.category), AlertSeverity.P4Low, s"${sig.detector} v${sig.version} score ${sig.score}")
    }
    (failures ++ evaluated ++ blocked ++ inputs).sortBy(_.timestamp)
