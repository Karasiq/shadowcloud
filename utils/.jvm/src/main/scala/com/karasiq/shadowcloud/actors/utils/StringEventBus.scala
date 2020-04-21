package com.karasiq.shadowcloud.actors.utils



import akka.actor.ActorRef
import akka.event.{ActorEventBus, LookupClassification}

final class StringEventBus[E](extract: E ⇒ String) extends ActorEventBus with LookupClassification {
  type Event = E
  type Classifier = String

  protected def mapSize(): Int = 16
  protected def classify(event: E): String = extract(event)
  protected def publish(event: E, subscriber: ActorRef): Unit = subscriber ! event
}
