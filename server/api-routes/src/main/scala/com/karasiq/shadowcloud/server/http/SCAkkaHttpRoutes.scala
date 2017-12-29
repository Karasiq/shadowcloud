package com.karasiq.shadowcloud.server.http

import akka.http.scaladsl.server._

trait SCAkkaHttpRoutes extends SCHttpServerSettings with SCAkkaHttpApiRoutes with SCAkkaHttpFileRoutes { self: Directives ⇒
  def scRoutes: Route = {
    encodeResponse(scApiRoute) ~ scFileRoute
  }
}
