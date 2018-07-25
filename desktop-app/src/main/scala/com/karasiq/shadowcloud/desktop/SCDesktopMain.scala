package com.karasiq.shadowcloud.desktop

import java.awt.Desktop
import java.net.URI
import java.nio.file.{Files, Paths}

import scala.concurrent.Future

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.impl.ConfigImpl

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.drive.SCDrive
import com.karasiq.shadowcloud.drive.fuse.SCFileSystem
import com.karasiq.shadowcloud.javafx.JavaFXContext
import com.karasiq.shadowcloud.persistence.h2.H2DB
import com.karasiq.shadowcloud.server.http.SCAkkaHttpServer

object SCDesktopMain extends App {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val config = {
    // Replace default ConfigFactory.load() config
    val classLoader = Thread.currentThread.getContextClassLoader
    ConfigImpl.computeCachedConfig(classLoader, "load", () ⇒ SCDesktopConfig.load())
  }

  implicit val actorSystem = ActorSystem("shadowcloud", config)
  // if (actorSystem.log.isDebugEnabled) actorSystem.logConfiguration() // log-config-on-start = on

  val sc = ShadowCloud(actorSystem)
  import sc.implicits.{executionContext, materializer}

  val httpServer = SCAkkaHttpServer(sc)

  if (!config.optional(_.getBoolean("shadowcloud.persistence.h2.disabled")).contains(true)) {
    // Init db
    H2DB(actorSystem).context
  }

  sc.init()

  // Start server
  val bindFuture = Http().bindAndHandle(httpServer.scWebAppRoutes, httpServer.httpServerConfig.host, httpServer.httpServerConfig.port)
  bindFuture.foreach { binding ⇒
    actorSystem.log.info("shadowcloud server running on {}", binding.localAddress)
  }

  new SCTrayIcon {
    def onOpen(): Unit = {
      if (Desktop.isDesktopSupported) {
        Desktop.getDesktop.browse(new URI(s"http://localhost:${httpServer.httpServerConfig.port}"))
      }
    }

    def onMount(): Future[Done] = {
      // Init FUSE file system
      val drive = SCDrive(actorSystem)
      val fuseConfig = drive.config.rootConfig.getConfigIfExists("fuse")

      // Fix FUSE properties
      val fuseWinFspDll = {
        val paths = fuseConfig.getStrings("winfsp.dll-paths")
        paths.find(p ⇒ Files.isRegularFile(Paths.get(p)))
      }
      fuseWinFspDll.foreach(path ⇒ System.setProperty("jnrfuse.winfsp.path", path))

      if (fuseConfig.withDefault(false, _.getBoolean("winfsp.fix-utf8"))) {
        System.setProperty("file.encoding", "UTF-8")
      }

      val fileSystem = SCFileSystem(drive.config, drive.dispatcher)
      val mountFuture = SCFileSystem.mountInSeparateThread(fileSystem)
      mountFuture.failed.foreach(actorSystem.log.error(_, "FUSE filesystem mount failed"))
      actorSystem.registerOnTermination(mountFuture.foreach(_ ⇒ fileSystem.umount()))
      mountFuture
    }

    def onExit(): Unit = {
      bindFuture
        .flatMap(_.unbind())
        .flatMap(_ ⇒ actorSystem.terminate())
        .onComplete(_ ⇒ System.exit(0))
    }
  }.addToTray()

  JavaFXContext(actorSystem)
    .initFuture
    .flatMap(_ ⇒ bindFuture.failed)
    .flatMap { error ⇒
      actorSystem.log.error(error, "Bind error")
      actorSystem.terminate()
    }
    .foreach(_ ⇒ System.exit(-1))
}
