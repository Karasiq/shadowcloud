package com.karasiq.shadowcloud.server.http

import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.server._
import akka.http.scaladsl.settings.ServerSettings
import com.typesafe.config.ConfigFactory

import com.karasiq.shadowcloud.ShadowCloud

object HttpServerMain extends HttpApp with App with PredefinedToResponseMarshallers with SCAkkaHttpServer {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] val config = {
    val defaultConfig = ConfigFactory.load()
    val serverAppConfig = ConfigFactory.load("sc-server-app").withFallback(defaultConfig)
    serverAppConfig
  }

  private[this] val actorSystem: ActorSystem = ActorSystem("shadowcloud-server", config)
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
  startServer(SCHttpSettings.host, SCHttpSettings.port, ServerSettings(actorSystem), actorSystem)
}
