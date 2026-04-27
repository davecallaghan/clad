package clad.monitoring

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import clad.integrity.{ComponentId, FailurePosture}
import clad.monitoring.test.*
import clad.monitoring.detectors.*
import java.time.{Instant, Duration}

class MonitoringEngineSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:

  val now: Instant = Instant.parse("2026-04-23T12:00:00Z")
  var sink: InMemoryAlertSink = _

  override def beforeEach(): Unit =
    sink = InMemoryAlertSink()
    TestEvents.counter = 0

  "MonitoringEngine with no detectors" should "produce no alerts" in {
    val engine = MonitoringEngine(Seq.empty, Seq.empty, sink)
    val alerts = engine.process(TestEvents.evaluationCompleted())
    alerts shouldBe empty
    sink.allAlerts shouldBe empty
  }

  "MonitoringEngine with ComponentHealthDetector" should "route ComponentFailure to alert" in {
    val engine = MonitoringEngine(Seq(ComponentHealthDetector()), Seq.empty, sink)
    val alerts = engine.process(TestEvents.componentFailure())
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Governance
    sink.allAlerts should have size 1
  }

  "MonitoringEngine with ConstraintViolationDetector" should "route ViolationDetected to alert" in {
    val engine = MonitoringEngine(Seq(ConstraintViolationDetector()), Seq.empty, sink)
    val alerts = engine.process(TestEvents.violationDetected())
    alerts should have size 1
    sink.alertsByCategory(AlertCategory.Compliance) should have size 1
  }

  it should "produce no alerts for non-matching events" in {
    val engine = MonitoringEngine(Seq(ComponentHealthDetector()), Seq.empty, sink)
    val alerts = engine.process(TestEvents.evaluationCompleted())
    alerts shouldBe empty
  }

  "MonitoringEngine with multiple detectors" should "run all detectors on each event" in {
    val engine = MonitoringEngine(
      Seq(ComponentHealthDetector(), ConstraintViolationDetector()), Seq.empty, sink
    )
    engine.process(TestEvents.componentFailure())
    engine.process(TestEvents.violationDetected())
    sink.allAlerts should have size 2
  }

  "MonitoringEngine with GhostRateDetector" should "detect ghost rate anomaly" in {
    val engine = MonitoringEngine(Seq.empty, Seq(GhostRateDetector(Duration.ofMinutes(15), threshold = 0.10)), sink)
    engine.process(TestEvents.evaluationCompleted(ts = now.plusSeconds(1)))
    engine.process(TestEvents.evaluationCompleted(ts = now.plusSeconds(2)))
    val alerts = engine.process(TestEvents.ghostDetected(ts = now.plusSeconds(3)))
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Governance
    sink.allAlerts should have size 1
  }

  "MonitoringEngine with BlockRateAnomalyDetector" should "detect block rate anomaly" in {
    val engine = MonitoringEngine(Seq.empty, Seq(BlockRateAnomalyDetector(Duration.ofMinutes(15), threshold = 0.20)), sink)
    engine.process(TestEvents.evaluationCompleted(ts = now.plusSeconds(1), source = ComponentId.Roc))
    engine.process(TestEvents.outputBlocked(ts = now.plusSeconds(2)))
    val alerts = engine.process(TestEvents.outputBlocked(ts = now.plusSeconds(3)))
    alerts should have size 1
    alerts.head.category shouldBe AlertCategory.Compliance
  }

  "MonitoringEngine with mixed detectors" should "run both stateless and window" in {
    val engine = MonitoringEngine(
      Seq(ComponentHealthDetector()),
      Seq(GhostRateDetector(Duration.ofMinutes(15), threshold = 0.0)),
      sink
    )
    engine.process(TestEvents.componentFailure(ts = now.plusSeconds(1)))
    engine.process(TestEvents.ghostDetected(ts = now.plusSeconds(2)))
    sink.allAlerts.size should be >= 2
  }

  "MonitoringEngine.connectTo" should "auto-process events from bus" in {
    val engine = MonitoringEngine(Seq(ConstraintViolationDetector()), Seq.empty, sink)
    val bus = InMemoryEventBus()
    engine.connectTo(bus)

    bus.publish(TestEvents.violationDetected())
    bus.publish(TestEvents.evaluationCompleted())
    bus.publish(TestEvents.violationDetected(severity = AlertSeverity.P3Medium))

    sink.allAlerts should have size 2
    bus.allPublished should have size 3
  }

  it should "work with empty bus" in {
    val engine = MonitoringEngine(Seq(ComponentHealthDetector()), Seq.empty, sink)
    val bus = InMemoryEventBus()
    engine.connectTo(bus)
    sink.allAlerts shouldBe empty
  }

  "InMemoryEventBus" should "deliver events to all subscribers" in {
    val bus = InMemoryEventBus()
    var received1 = Vector.empty[GovernanceEvent]
    var received2 = Vector.empty[GovernanceEvent]
    bus.subscribe(e => received1 = received1 :+ e)
    bus.subscribe(e => received2 = received2 :+ e)
    bus.publish(TestEvents.evaluationCompleted())
    received1 should have size 1
    received2 should have size 1
  }

  it should "track all published events" in {
    val bus = InMemoryEventBus()
    bus.publish(TestEvents.evaluationCompleted())
    bus.publish(TestEvents.componentFailure())
    bus.allPublished should have size 2
  }

  it should "clear listeners and events" in {
    val bus = InMemoryEventBus()
    var received = 0
    bus.subscribe(_ => received += 1)
    bus.publish(TestEvents.evaluationCompleted())
    received shouldBe 1
    bus.clear()
    bus.publish(TestEvents.evaluationCompleted())
    received shouldBe 1
    bus.allPublished should have size 1
  }
