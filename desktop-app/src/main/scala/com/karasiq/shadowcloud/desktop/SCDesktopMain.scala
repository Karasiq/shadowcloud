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
    val autoParallelismConfig = {
      val cpuAvailable = Runtime.getRuntime.availableProcessors()
      val parallelism = math.max(8, math.min(1, cpuAvailable / 2))
      ConfigFactory.parseString(s"shadowcloud.parallelism.default = $parallelism")
    }

    val substitutionsConfig = ConfigFactory.parseResourcesAnySyntax("sc-substitutions")

    val defaultConfig = ConfigFactory.defaultOverrides()
      .withFallback(ConfigFactory.defaultApplication())
      .withFallback(ConfigFactory.defaultReference())
      /* .withFallback {
        // reference.conf without ".resolve()" workaround

        val classLoader = Thread.currentThread().getContextClassLoader
        ConfigImpl.computeCachedConfig(classLoader, "scReference", () ⇒ {
          val parseOptions = ConfigParseOptions.defaults.setClassLoader(classLoader)
          val unresolvedResources = Parseable.newResources("reference.conf", parseOptions)
            .parse
            .toConfig

          ConfigImpl.systemPropertiesAsConfig()
            .withFallback(unresolvedResources)
        })
      } */

    val serverAppConfig = {
      val fileConfig = {
        val optionalConfFile = Paths.get("shadowcloud.conf")
        if (Files.isRegularFile(optionalConfFile))
          ConfigFactory.parseFile(optionalConfFile.toFile)
        else
          ConfigUtils.emptyConfig
      }

      val desktopConfig = ConfigFactory.parseResourcesAnySyntax("sc-desktop")

      fileConfig
        .withFallback(substitutionsConfig)
        .withFallback(autoParallelismConfig)
        .withFallback(desktopConfig)
        .withFallback(defaultConfig)
        .resolve()
    }

    serverAppConfig
  }

  implicit val actorSystem = ActorSystem("shadowcloud", config)
  // if (actorSystem.log.isDebugEnabled) actorSystem.logConfiguration() // log-config-on-start = on

  val sc = ShadowCloud(actorSystem)
  import sc.implicits.{executionContext, materializer}

  val httpServer = SCAkkaHttpServer(sc)
  H2DB(actorSystem).context // Init db
  sc.actors.regionSupervisor // Init actor

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
