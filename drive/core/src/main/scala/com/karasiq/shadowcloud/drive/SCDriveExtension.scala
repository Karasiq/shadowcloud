package com.karasiq.shadowcloud.drive

import akka.actor.{ActorContext, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.drive.config.SCDriveConfig

object SCDrive extends ExtensionId[SCDriveExtension] with ExtensionIdProvider {
  def apply()(implicit context: ActorContext): SCDriveExtension = {
    apply(context.system)
  }

  def createExtension(system: ExtendedActorSystem): SCDriveExtension = {
    new SCDriveExtension(system)
  }

  def lookup(): ExtensionId[_ <: Extension] = {
    SCDrive
  }
}

class SCDriveExtension(system: ExtendedActorSystem) extends Extension {
  val config          = SCDriveConfig(system.settings.config.getConfig("shadowcloud.drive"))
  lazy val dispatcher = system.actorOf(VirtualFSDispatcher.props(config), "sc-drive-dispatcher")
}
