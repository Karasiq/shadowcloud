package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Stash, Terminated}
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.RegionSupervisor.RenewStorageSubscriptions
import com.karasiq.shadowcloud.actors.StorageContainer.SetProps
import com.karasiq.shadowcloud.actors.internal.StorageInstantiator
import com.karasiq.shadowcloud.actors.utils.ContainerActor
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.duration._

private[actors] object StorageContainer {
  sealed trait Message
  final case class SetProps(storageProps: StorageProps) extends Message

  def props(instantiator: StorageInstantiator, storageId: StorageId): Props = {
    Props(new StorageContainer(instantiator, storageId))
  }
}

//noinspection ActorMutableStateInspection
private[actors] final class StorageContainer(instantiator: StorageInstantiator, storageId: StorageId)
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
        case Terminated(ref) ⇒
          log.error("Watched actor terminated: {}", ref)
          context.stop(self)

        case msg if sender() == storage ⇒
          context.parent ! msg

        case msg ⇒
          storage.forward(msg)
      }
    })

    val id = Utils.uniqueActorName(storageId)
    val actor = context.actorOf(props, id)
    val healthSupervisor = context.actorOf(StorageHealthSupervisor.props(actor, 30 seconds, 5), s"$id-health-sv")
    afterStart(healthSupervisor)
  }

  override def afterStart(actor: ActorRef): Unit = {
    sc.actors.regionSupervisor ! RenewStorageSubscriptions(storageId)
    super.afterStart(actor)
  }
}
