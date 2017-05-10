package com.karasiq.shadowcloud.actors

import scala.language.postfixOps

import akka.actor.{Actor, Props, Stash}

import com.karasiq.shadowcloud.actors.utils.ContainerActor
import com.karasiq.shadowcloud.actors.RegionContainer.SetConfig
import com.karasiq.shadowcloud.config.RegionConfig

object RegionContainer {
  sealed trait Message
  case class SetConfig(regionConfig: RegionConfig)

  def props(regionId: String): Props = {
    Props(classOf[RegionContainer], regionId)
  }
}

class RegionContainer(regionId: String) extends Actor with Stash with ContainerActor {
  var regionConfig: RegionConfig = RegionConfig(regionId)

  def receiveDefault: Receive = {
    case SetConfig(rc) ⇒
      this.regionConfig = rc
      restartActor()
  }

  def startActor(): Unit = {
    val dispatcher = context.actorOf(RegionDispatcher.props(regionId, regionConfig))
    afterStart(dispatcher)
  }
}
