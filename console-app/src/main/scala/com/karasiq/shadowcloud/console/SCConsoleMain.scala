package com.karasiq.shadowcloud.console

import akka.actor.ActorSystem
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.drive.fuse.SCFuseHelper
import com.typesafe.config.impl.ConfigImpl

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

object SCConsoleMain extends App {
  private[this] val config = {
    // Replace default ConfigFactory.load() config
    val classLoader = Thread.currentThread.getContextClassLoader
    ConfigImpl.computeCachedConfig(classLoader, "load", () ⇒ SCConsoleConfig.load())
  }

  implicit val actorSystem = ActorSystem("shadowcloud", config)

  val sc = ShadowCloud(actorSystem)
  import sc.implicits.executionContext

  sys.addShutdownHook {
    Try(sc.shutdown())
    Try(Await.result(actorSystem.terminate(), 15 seconds))
  }

  sc.init()

  if (config.optional(_.getBoolean("shadowcloud.drive.fuse.auto-mount")).contains(true)) {
    val eventualDone = SCFuseHelper.mount()
    eventualDone.foreach(_ ⇒ actorSystem.log.info("shadowcloud FUSE filesystem mount success"))
    eventualDone.failed.foreach(_ => sys.exit(0))
  }
}
