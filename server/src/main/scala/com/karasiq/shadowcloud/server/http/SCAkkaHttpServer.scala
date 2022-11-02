package com.karasiq.shadowcloud.server.http

import java.security.MessageDigest

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.Credentials
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.server.http.static.SCAkkaHttpStaticRoutes
import com.karasiq.shadowcloud.server.http.webzinc.SCAkkaHttpWebZincRoutes
import org.apache.commons.codec.binary.Hex

object SCAkkaHttpServer {
  def apply(sc: ShadowCloudExtension): SCAkkaHttpServer = {
    new SCAkkaHttpServer(sc)
  }
}

class SCAkkaHttpServer(protected val sc: ShadowCloudExtension)
    extends Directives
    with PredefinedToResponseMarshallers
    with SCAkkaHttpRoutes
    with SCAkkaHttpStaticRoutes
    with SCAkkaHttpWebZincRoutes {

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  lazy val scWebAppRoutes: Route = httpServerConfig.passwordHash match {
    case Some(passHash) ⇒
      def hashString(str: String) = {
        val sha256      = MessageDigest.getInstance("SHA-256")
        val digestBytes = sha256.digest(str.getBytes)
        Hex.encodeHexString(digestBytes).toLowerCase
      }

      authenticateBasic(
        "shadowcloud",
        {
          case c @ Credentials.Provided("sc") if c.verify(passHash.toLowerCase, hashString) ⇒ Some(allRoutes)
          case _                                                                            ⇒ None
        }
      )(identity)

    case None ⇒
      allRoutes
  }

  private[this] def allRoutes: Route = scRoutes ~ scWebZincRoute ~ scStaticRoute
}
