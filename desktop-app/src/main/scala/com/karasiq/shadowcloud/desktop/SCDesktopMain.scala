package com.karasiq.shadowcloud.desktop

import java.awt.Desktop
import java.net.URI

import akka.Done
import akka.actor.ActorSystem
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.drive.fuse.SCFuseHelper
import com.karasiq.shadowcloud.server.http.SCAkkaHttpServer
import com.typesafe.config.impl.ConfigImpl

import scala.concurrent.Future
import scala.util.Try

object SCDesktopMain extends App {
  private[this] val config = {
    // Replace default ConfigFactory.load() config
    val classLoader = Thread.currentThread.getContextClassLoader
    ConfigImpl.computeCachedConfig(classLoader, "load", () ⇒ SCDesktopConfig.load())
  }

  implicit val actorSystem = ActorSystem("shadowcloud", config)

  val sc = ShadowCloud(actorSystem)
  import sc.implicits.executionContext
  sc.init()

  val httpServer = SCAkkaHttpServer(sc)

  new SCTrayIcon {
    def onOpen(): Unit =
      if (Desktop.isDesktopSupported)
        Desktop.getDesktop.browse(new URI(s"http://localhost:${httpServer.httpServerConfig.port}"))

    def onMount(): Future[Done] =
      SCFuseHelper.mount()

    def onExit(): Unit = {
      Try(sc.shutdown())
      sys.exit(0)
    }
  }.addToTray()

  sys.addShutdownHook {
    sc.shutdown()
  }
}
