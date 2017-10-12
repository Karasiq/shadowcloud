package com.karasiq.shadowcloud.server.http

import java.nio.file.{Files, Paths}

import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.ServerSettings
import com.typesafe.config.ConfigFactory

import com.karasiq.common.configs.ConfigUtils
import com.karasiq.shadowcloud.ShadowCloud

object HttpServerMain extends HttpApp with App with PredefinedToResponseMarshallers with SCAkkaHttpServer {
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

      val serverConfig = ConfigFactory.load("sc-server-app")
      fileConfig.withFallback(serverConfig).withFallback(defaultConfig)
    }
    serverAppConfig
  }

  val actorSystem = ActorSystem("shadowcloud", config)
  protected val sc = ShadowCloud(actorSystem)

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected val routes: Route = {
    scRoute ~ staticFilesRoute
  }

  private[this] def staticFilesRoute: Route = {
    encodeResponse {
      pathEndOrSingleSlash {
        getFromResource("webapp/index.html")
      } ~ {
        getFromResourceDirectory("webapp")
      }
    }
  }

  // -----------------------------------------------------------------------
  // Start server
  // -----------------------------------------------------------------------
  sc.actors.regionSupervisor // Start actor
  startServer(SCHttpSettings.host, SCHttpSettings.port, ServerSettings(actorSystem), actorSystem)
}
