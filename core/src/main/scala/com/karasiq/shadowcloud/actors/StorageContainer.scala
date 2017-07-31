package com.karasiq.shadowcloud.actors

import scala.language.postfixOps

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.ContainerActor
import com.karasiq.shadowcloud.actors.StorageContainer.SetProps
import com.karasiq.shadowcloud.actors.internal.StorageInstantiator
import com.karasiq.shadowcloud.actors.RegionSupervisor.RenewStorageSubscriptions
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.Utils

private[actors] object StorageContainer {
  sealed trait Message
  case class SetProps(storageProps: StorageProps) extends Message

  def props(instantiator: StorageInstantiator, storageId: String): Props = {
    Props(new StorageContainer(instantiator, storageId))
  }
}

private[actors] final class StorageContainer(instantiator: StorageInstantiator, storageId: String)
  extends Actor with ActorLogging with Stash with ContainerActor {

  private[this] val sc = ShadowCloud()
  private[this] var storageProps: StorageProps = StorageProps.inMemory

  def receive: Receive = {
    case SetProps(props) ⇒
      log.warning("Storage props changed: {}", props)
      this.storageProps = props
      restartActor()
  }

  def startActor(): Unit = {
    val props = Props(new Actor {
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
    })
    val actor = context.actorOf(props, Utils.uniqueActorName(storageId))
    afterStart(actor)
  }

  override def afterStart(actor: ActorRef): Unit = {
    sc.actors.regionSupervisor ! RenewStorageSubscriptions(storageId)
    super.afterStart(actor)
  }
}
