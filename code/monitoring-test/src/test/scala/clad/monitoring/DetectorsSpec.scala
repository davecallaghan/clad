package clad.monitoring

import clad.monitoring.detectors.*
import clad.monitoring.test.TestEvents
import clad.integrity.{ComponentId, FailurePosture}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.time.{Duration, Instant}

class DetectorsSpec extends AnyFlatSpec with Matchers:

  val now: Instant = Instant.parse("2026-04-23T12:00:00Z")

  // --- ComponentHealthDetector ---

  "ComponentHealthDetector" should "fire on ComponentFailure with FailClosed as P2High" in {
    val detector = ComponentHealthDetector()
    val alerts = detector.detect(TestEvents.componentFailure(component = ComponentId.Epg, posture = FailurePosture.FailClosed, ts = now))
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Governance
    alerts.head.severity shouldBe AlertSeverity.P2High
    alerts.head.title should include("epg")
    alerts.head.detectorId shouldBe "component_health"
  }

  it should "use P3Medium for FailOpenFlagged" in {
    val detector = ComponentHealthDetector()
    val alerts = detector.detect(TestEvents.componentFailure(posture = FailurePosture.FailOpenFlagged))
    alerts.head.severity shouldBe AlertSeverity.P3Medium
  }

  it should "use P3Medium for FailOpen" in {
    val detector = ComponentHealthDetector()
    val alerts = detector.detect(TestEvents.componentFailure(posture = FailurePosture.FailOpen))
    alerts.head.severity shouldBe AlertSeverity.P3Medium
  }

  it should "ignore non-ComponentFailure events" in {
    val detector = ComponentHealthDetector()
    detector.detect(TestEvents.evaluationCompleted()) shouldBe empty
    detector.detect(TestEvents.violationDetected()) shouldBe empty
  }

  // --- ConstraintViolationDetector ---

  "ConstraintViolationDetector" should "fire on ViolationDetected" in {
    val detector = ConstraintViolationDetector()
    val alerts = detector.detect(TestEvents.violationDetected(property = "phi_detected", severity = AlertSeverity.P1Critical))
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Compliance
    alerts.head.severity shouldBe AlertSeverity.P1Critical
    alerts.head.title should include("phi_detected")
    alerts.head.detectorId shouldBe "constraint_violation"
  }

  it should "pass through severity from event" in {
    val detector = ConstraintViolationDetector()
    val alerts = detector.detect(TestEvents.violationDetected(severity = AlertSeverity.P4Low))
    alerts.head.severity shouldBe AlertSeverity.P4Low
  }

  it should "ignore non-ViolationDetected events" in {
    val detector = ConstraintViolationDetector()
    detector.detect(TestEvents.componentFailure()) shouldBe empty
    detector.detect(TestEvents.evaluationCompleted()) shouldBe empty
  }

  // --- GhostRateDetector ---

  "GhostRateDetector" should "not fire when ghost rate is below threshold" in {
    val detector = GhostRateDetector(Duration.ofMinutes(15), threshold = 0.10)
    for i <- 1 to 19 do
      detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(i)))
    val alerts = detector.ingest(TestEvents.ghostDetected(ts = now.plusSeconds(20)))
    alerts shouldBe empty
  }

  it should "fire when ghost rate exceeds threshold" in {
    val detector = GhostRateDetector(Duration.ofMinutes(15), threshold = 0.05)
    for i <- 1 to 8 do
      detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(i)))
    detector.ingest(TestEvents.ghostDetected(ghostId = "g1", ts = now.plusSeconds(9)))
    val alerts = detector.ingest(TestEvents.ghostDetected(ghostId = "g2", ts = now.plusSeconds(10)))
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Governance
    alerts.head.severity shouldBe AlertSeverity.P2High
    alerts.head.metadata("ghost_rate").toDouble should be > 0.05
  }

  it should "not fire on empty window (only config events)" in {
    val detector = GhostRateDetector()
    val alerts = detector.ingest(TestEvents.configChange())
    alerts shouldBe empty
  }

  it should "evict events outside the window" in {
    val detector = GhostRateDetector(Duration.ofSeconds(10), threshold = 0.50)
    detector.ingest(TestEvents.ghostDetected(ghostId = "g1", ts = now))
    val alerts = detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(15)))
    alerts shouldBe empty
    detector.currentWindow should have size 1
  }

  // --- BlockRateAnomalyDetector ---

  "BlockRateAnomalyDetector" should "not fire when block rate is below threshold" in {
    val detector = BlockRateAnomalyDetector(Duration.ofMinutes(15), threshold = 0.30)
    for i <- 1 to 9 do
      detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(i), source = ComponentId.Roc))
    val alerts = detector.ingest(TestEvents.outputBlocked(ts = now.plusSeconds(10)))
    alerts shouldBe empty
  }

  it should "fire when block rate exceeds threshold" in {
    val detector = BlockRateAnomalyDetector(Duration.ofMinutes(15), threshold = 0.20)
    for i <- 1 to 2 do
      detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(i), source = ComponentId.Roc))
    detector.ingest(TestEvents.outputBlocked(ts = now.plusSeconds(3)))
    detector.ingest(TestEvents.outputBlocked(ts = now.plusSeconds(4)))
    val alerts = detector.ingest(TestEvents.outputBlocked(ts = now.plusSeconds(5)))
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Compliance
    alerts.head.severity shouldBe AlertSeverity.P3Medium
    alerts.head.metadata("block_rate").toDouble should be > 0.20
  }

  it should "ignore EPG evaluations (only count ROC)" in {
    val detector = BlockRateAnomalyDetector(Duration.ofMinutes(15), threshold = 0.20)
    for i <- 1 to 10 do
      detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(i), source = ComponentId.Epg))
    val alerts = detector.ingest(TestEvents.outputBlocked(ts = now.plusSeconds(11)))
    alerts should have size 1
  }

  // --- AbstractWindowDetector sliding window ---

  "AbstractWindowDetector" should "maintain sliding window" in {
    val detector = GhostRateDetector(Duration.ofSeconds(5), threshold = 1.0)
    detector.ingest(TestEvents.evaluationCompleted(ts = now))
    detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(3)))
    detector.ingest(TestEvents.evaluationCompleted(ts = now.plusSeconds(7)))
    detector.currentWindow should have size 2
  }
