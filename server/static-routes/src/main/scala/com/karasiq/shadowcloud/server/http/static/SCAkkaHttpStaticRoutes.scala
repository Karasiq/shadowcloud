package com.karasiq.shadowcloud.server.http.static

import akka.http.scaladsl.server.{Directives, Route}

import com.karasiq.shadowcloud.server.http.{SCAkkaHttpApiRoutes, SCHttpServerSettings}

trait SCAkkaHttpStaticRoutes { self: SCAkkaHttpApiRoutes with SCHttpServerSettings with Directives ⇒
  val scStaticRoute: Route = {
    encodeResponse {
      pathEndOrSingleSlash {
        getFromResource("webapp/index.html")
      } ~ {
        getFromResourceDirectory("webapp")
      }
    }
  }
}
