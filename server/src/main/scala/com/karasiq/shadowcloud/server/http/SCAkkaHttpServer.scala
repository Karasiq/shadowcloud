package com.karasiq.shadowcloud.server.http

import akka.http.scaladsl.server._

trait SCAkkaHttpServer extends SCHttpServerSettings with SCAkkaHttpApiServer with SCAkkaHttpFileServer { self: Directives ⇒
  def scRoute: Route = {
    encodeResponse(scApiRoute) ~ scFileRoute
  }
}
