package com.karasiq.shadowcloud.persistence.h2

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}

import com.karasiq.shadowcloud.ShadowCloud

object H2DB extends ExtensionId[H2DBExtension] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem): H2DBExtension = {
    new H2DBExtension(system)
  }

  def lookup(): ExtensionId[_ <: Extension] = {
    H2DB
  }
}

class H2DBExtension(system: ExtendedActorSystem) extends Extension {
  lazy val sc = ShadowCloud(system)
  lazy val config = sc.rootConfig.getConfig("persistence.h2")
  lazy val dispatcher = system.dispatchers.lookup("shadowcloud.persistence.h2.dispatcher")

  private[this] def getDbPassword: String = {
    sc.passwords.masterPassword.replace(' ', '_')
  }

  lazy val context = {
    val context = new H2Context(config, getDbPassword)
    system.registerOnTermination(context.db.close())
    context
  }
}
