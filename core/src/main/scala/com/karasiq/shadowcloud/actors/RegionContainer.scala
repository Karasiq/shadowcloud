package com.karasiq.shadowcloud.actors

import scala.language.postfixOps

import akka.actor.{Actor, Props, Stash}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.ContainerActor
import com.karasiq.shadowcloud.actors.RegionContainer.SetConfig
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.utils.Utils

object RegionContainer {
  sealed trait Message
  case class SetConfig(regionConfig: RegionConfig)

  def props(regionId: String): Props = {
    Props(classOf[RegionContainer], regionId)
  }
}

class RegionContainer(regionId: String) extends Actor with Stash with ContainerActor {
  private[this] val sc = ShadowCloud()
  var regionConfig: RegionConfig = sc.regionConfig(regionId)

  def receive: Receive = {
    case SetConfig(rc) ⇒
      this.regionConfig = rc
      restartActor()
  }

  def startActor(): Unit = {
    val dispatcher = context.actorOf(RegionDispatcher.props(regionId, regionConfig), Utils.uniqueActorName(regionId))
    afterStart(dispatcher)
  }
}
