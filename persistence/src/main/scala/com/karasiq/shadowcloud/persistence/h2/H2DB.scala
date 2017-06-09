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

  private[this] def getDbPassword: String = {
    sc.password.masterPassword.replace(' ', '_')
  }

  lazy val context = new H2Context(config, getDbPassword)
}
