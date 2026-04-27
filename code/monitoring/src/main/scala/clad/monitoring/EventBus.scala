package clad.monitoring

trait EventBus:
  def publish(event: GovernanceEvent): Unit
  def subscribe(listener: GovernanceEvent => Unit): Unit
