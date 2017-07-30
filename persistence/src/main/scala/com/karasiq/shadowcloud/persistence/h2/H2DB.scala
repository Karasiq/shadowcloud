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
  object settings {
    private[this] val sc = ShadowCloud(system)
    val config = sc.rootConfig.getConfig("persistence.h2")

    private[H2DBExtension] def getDbPassword(): String = {
      sc.passwords.getOrAsk("persistence.h2.password", "h2-db").replace(' ', '_')
    }
  }

  lazy val dispatcher = system.dispatchers.lookup("shadowcloud.persistence.h2.dispatcher")

  lazy val context = {
    val context = new H2Context(settings.config, settings.getDbPassword())
    system.registerOnTermination(context.db.close())
    context
  }


}
