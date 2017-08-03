package com.karasiq.shadowcloud.persistence.h2

import scala.concurrent.ExecutionContext

import akka.actor.{ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import com.typesafe.config.Config

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.persistence.h2.H2Context.ContextT

object H2DB extends ExtensionId[H2DBExtension] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem): H2DBExtension = {
    new H2DBExtension(system)
  }

  def lookup(): ExtensionId[_ <: Extension] = {
    H2DB
  }
}

final class H2DBExtension(system: ExtendedActorSystem) extends Extension {
  object settings {
    private[this] val sc = ShadowCloud(system)
    val config: Config = sc.config.rootConfig.getConfig("persistence.h2")

    implicit def executionContext: ExecutionContext = {
      val dispatcherName = config.getString("dispatcher")
      system.dispatchers.lookup(dispatcherName)
    }

    private[H2DBExtension] def getDbPassword(): String = {
      sc.passwords.getOrAsk("persistence.h2.password", "h2-db").replace(' ', '_')
    }
  }

  lazy val context: ContextT = {
    val context = H2Context(settings.config, settings.getDbPassword())
    system.registerOnTermination(context.close())
    context
  }
}
