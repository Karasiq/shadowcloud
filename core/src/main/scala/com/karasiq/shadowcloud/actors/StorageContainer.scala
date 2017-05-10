package com.karasiq.shadowcloud.actors

import scala.language.postfixOps

import akka.actor.{Actor, Props, Stash}

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

class StorageContainer(instantiator: StorageInstantiator, storageId: String) extends Actor with Stash with ContainerActor {
  var storageProps: StorageProps = StorageProps.inMemory

  def receiveDefault: Receive = {
    case SetProps(props) ⇒
      this.storageProps = props
      restartActor()
  }

  def startActor(): Unit = {
    val actor = instantiator.createStorage(storageId, storageProps)
    afterStart(actor)
  }
}
