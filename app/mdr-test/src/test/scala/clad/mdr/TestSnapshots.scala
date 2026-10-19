package clad.mdr

import clad.core.{Constraint, Level, PropertyId}
import clad.evaluation.MechanicalDetail
import clad.gateway.pipeline.InputSignal
import clad.integrity.*
import clad.monitoring.AlertSeverity
import clad.runtime.{AuditEntry, EvaluabilityClass}
import java.nio.file.{Files, Paths}
import java.time.{Duration, Instant}

/** Snapshot builders for detector tests (no database). */
object TestSnapshots:
  val now: Instant = Instant.parse("2026-10-19T12:00:00Z")
  val cfg: MdrConfig = MdrConfig.load(Paths.get(getClass.getResource("/runtime.yaml").toURI)).fold(e => throw AssertionError(e), identity)
  val window: Window = Window(now.minusSeconds(cfg.intervalSeconds + cfg.inFlightGraceSeconds), now)
  val empty: Snapshot = Snapshot(window, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Set.empty,
    Set.empty, Set.empty, Vector.empty)

  def ago(seconds: Long): Instant = now.minusSeconds(seconds)
  def id(n: Int): String = f"0192f0c0-0000-7000-8000-$n%012d"

  def gil(iid: String, at: Instant, project: String = "agent", principal: String = "api", meta: (String, String)*): GilRow =
    GilRow(iid, at, project, principal, "Critical", ujson.Obj.from(meta.map((k, v) => k -> ujson.Str(v))))

  def entry(property: String, satisfied: Boolean, at: Instant = now): AuditEntry =
    AuditEntry(Constraint.Prohibition(PropertyId.unsafe(property), Level.Enterprise), "1", EvaluabilityClass.Mechanical,
      satisfied, MechanicalDetail(!satisfied), at)

  def record(iid: String, component: String, outcome: String, at: Instant = now, entries: Vector[AuditEntry] = Vector.empty,
             config: String = "sha256:c"): RecordRow =
    RecordRow(iid, component, Some(outcome), config, entries, at)

  def degraded(iid: String, at: Instant, component: ComponentId = ComponentId.Roc,
               posture: FailurePosture = FailurePosture.FailClosed): SignedDegradedRecord =
    val (sup, _) = clad.audit.Ed25519KeyManagement.generate("5upervis0r000001")
    val r = DegradedAuditRecord(InteractionId(iid), at, component, DegradedStatus.Degraded, "upstream: 503", posture,
      FailurePosture.actionFor(posture))
    SignedDegradedRecord.sign(r, sup, ComponentId.Supervisor).fold(e => throw AssertionError(e.toString), identity)

  def infra(at: Instant, iid: Option[String], status: Int = 200): InfraCall = InfraCall(at, "api.deepinfra.com", status, iid)

  def injection(iid: String, at: Instant): (String, InputSignal, Instant) =
    (iid, InputSignal("ignore_instructions", "1", "injection", 1.0), at)
