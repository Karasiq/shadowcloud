package com.karasiq.shadowcloud.desktop

import java.awt.Desktop
import java.net.URI
import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.config.ConfigFactory

import com.karasiq.common.configs.ConfigUtils
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.javafx.JavaFXContext
import com.karasiq.shadowcloud.persistence.h2.H2DB
import com.karasiq.shadowcloud.server.http.SCAkkaHttpServer

object SCDesktopMain extends App {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val config = {
    val defaultConfig = ConfigFactory.load()
    val serverAppConfig = {
      val fileConfig = if (Files.isRegularFile(Paths.get("shadowcloud.conf")))
        ConfigFactory.parseFile(new java.io.File("shadowcloud.conf"))
      else
        ConfigUtils.emptyConfig

      val serverConfig = ConfigFactory.load("sc-desktop")
      fileConfig.withFallback(serverConfig).withFallback(defaultConfig)
    }
    serverAppConfig
  }

  implicit val actorSystem = ActorSystem("shadowcloud", config)
  val sc = ShadowCloud(actorSystem)
  val httpServer = SCAkkaHttpServer(sc)

  import sc.implicits.{executionContext, materializer}
  H2DB(actorSystem).context // Init db
  sc.actors.regionSupervisor // Init actor
  val bindFuture = Http().bindAndHandle(httpServer.scWebAppRoutes, httpServer.httpServerSettings.host, httpServer.httpServerSettings.port)

  new SCTrayIcon {
    def onOpen(): Unit = {
      if (Desktop.isDesktopSupported) {
        Desktop.getDesktop.browse(new URI(s"http://localhost:${httpServer.httpServerSettings.port}"))
      }
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
