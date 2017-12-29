package com.karasiq.shadowcloud.server.http

import scala.language.postfixOps

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.server._

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.server.http.static.SCAkkaHttpStaticRoutes
import com.karasiq.shadowcloud.server.http.webzinc.SCAkkaHttpWebZincRoutes

object SCAkkaHttpServer {
  def apply(sc: ShadowCloudExtension): SCAkkaHttpServer = {
    new SCAkkaHttpServer(sc)
  }
}

class SCAkkaHttpServer(protected val sc: ShadowCloudExtension) extends Directives with PredefinedToResponseMarshallers
    with SCAkkaHttpRoutes with SCAkkaHttpStaticRoutes with SCAkkaHttpWebZincRoutes {

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  lazy val scWebAppRoutes: Route = {
    scRoutes ~ scWebZincRoute ~ scStaticRoute
  }
}
