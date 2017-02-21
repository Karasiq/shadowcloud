package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props, SupervisorStrategy}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StorageInstantiator

import scala.language.postfixOps

object StorageSupervisor {
  // Props
  def props(instantiator: StorageInstantiator, storageId: String, props: StorageProps): Props = {
    Props(classOf[StorageSupervisor], instantiator, storageId, props)
  }
}

class StorageSupervisor(instantiator: StorageInstantiator, storageId: String, props: StorageProps) extends Actor with ActorLogging {
  val storage = instantiator.createStorage(storageId, props)

  override def receive: Receive = {
    case msg if sender() == storage ⇒
      context.parent ! msg

    case msg: ChunkIODispatcher.Message ⇒
      storage.forward(msg)
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
