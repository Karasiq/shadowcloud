package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props, SupervisorStrategy}
import com.karasiq.shadowcloud.actors.internal.StorageInstantiator
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

private[actors] object StorageSupervisor {
  // Props
  def props(instantiator: StorageInstantiator, storageId: String, props: StorageProps): Props = {
    Props(classOf[StorageSupervisor], instantiator, storageId, props)
  }
}

private final class StorageSupervisor(instantiator: StorageInstantiator, storageId: String, props: StorageProps) extends Actor with ActorLogging {
  val storage = instantiator.createStorage(storageId, props)

  override def receive: Receive = {
    case msg ⇒
      if (sender() == storage) {
        context.parent ! msg
      } else {
        storage.forward(msg)
      }
  }

  override def postStop(): Unit = {
    context.stop(storage)
    super.postStop()
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _ ⇒
      SupervisorStrategy.Restart
  }
}
