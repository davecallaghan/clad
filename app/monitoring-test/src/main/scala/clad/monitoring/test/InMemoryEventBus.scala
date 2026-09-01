package clad.monitoring.test

import clad.monitoring.*

class InMemoryEventBus extends EventBus:
  private var listeners: Vector[GovernanceEvent => Unit] = Vector.empty
  private var publishedEvents: Vector[GovernanceEvent] = Vector.empty

  def publish(event: GovernanceEvent): Unit =
    publishedEvents = publishedEvents :+ event
    listeners.foreach(_(event))

  def subscribe(listener: GovernanceEvent => Unit): Unit =
    listeners = listeners :+ listener

  def allPublished: Vector[GovernanceEvent] = publishedEvents
  def clear(): Unit =
    publishedEvents = Vector.empty
    listeners = Vector.empty
