package clad.monitoring

class MonitoringEngine(
  eventDetectors: Seq[EventDetector],
  windowDetectors: Seq[WindowDetector],
  alertSink: AlertSink
):
  def process(event: GovernanceEvent): Vector[Alert] =
    val eventAlerts = eventDetectors.flatMap(_.detect(event)).toVector
    val windowAlerts = windowDetectors.flatMap(_.ingest(event)).toVector
    val allAlerts = eventAlerts ++ windowAlerts
    allAlerts.foreach(alertSink.emit)
    allAlerts

  def connectTo(bus: EventBus): Unit =
    bus.subscribe(event => process(event))
