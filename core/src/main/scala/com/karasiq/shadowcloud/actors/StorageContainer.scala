package com.karasiq.shadowcloud.actors

import scala.language.postfixOps

import akka.actor.{Actor, ActorLogging, Props, Stash}

import com.karasiq.shadowcloud.actors.utils.ContainerActor
import com.karasiq.shadowcloud.actors.StorageContainer.SetProps
import com.karasiq.shadowcloud.actors.internal.StorageInstantiator
import com.karasiq.shadowcloud.storage.props.StorageProps

object StorageContainer {
  sealed trait Message
  case class SetProps(storageProps: StorageProps) extends Message

  def props(instantiator: StorageInstantiator, storageId: String): Props = {
    Props(classOf[StorageContainer], instantiator, storageId)
  }
}

class StorageContainer(instantiator: StorageInstantiator, storageId: String) extends Actor with ActorLogging with Stash with ContainerActor {
  var storageProps: StorageProps = StorageProps.inMemory

  def receive: Receive = {
    case SetProps(props) ⇒
      log.info("Storage props changed: {}", props)
      this.storageProps = props
      restartActor()
  }

  def startActor(): Unit = {
    val actor = context.actorOf(Props(new Actor {
      private[this] val storage = instantiator.createStorage(storageId, storageProps)

      override def preStart(): Unit = {
        super.preStart()
        context.watch(storage)
      }

      def receive: Receive = {
        /* case Terminated(`storage`) ⇒
          context.stop(self) */

        case msg if sender() == storage ⇒
          context.parent ! msg

        case msg ⇒
          storage.forward(msg)
      }
    }))
    afterStart(actor)
  }
}
