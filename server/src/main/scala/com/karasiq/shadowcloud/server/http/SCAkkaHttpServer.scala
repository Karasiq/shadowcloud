package com.karasiq.shadowcloud.server.http

import scala.language.postfixOps

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.server._

import com.karasiq.shadowcloud.ShadowCloudExtension

object SCAkkaHttpServer {
  def apply(sc: ShadowCloudExtension): SCAkkaHttpServer = {
    new SCAkkaHttpServer(sc)
  }
}

class SCAkkaHttpServer(protected val sc: ShadowCloudExtension)
  extends Directives with PredefinedToResponseMarshallers with SCAkkaHttpRoutes {

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  val scWebAppRoutes: Route = {
    scRoutes ~ staticFilesRoute
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
}
