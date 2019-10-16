package com.karasiq.shadowcloud.server.http

import akka.http.scaladsl.Http
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.providers.LifecycleHook

import scala.concurrent.Await
import scala.language.postfixOps

private[http] final class SCAkkaHttpServerLifecycleHook(sc: ShadowCloudExtension) extends LifecycleHook {
  @volatile
  private[this] var serverBinding: Http.ServerBinding = _

  override def initialize(): Unit = {
    import sc.implicits.{actorSystem, executionContext, materializer}

    val httpServer = SCAkkaHttpServer(sc)
    // Start server
    val bindFuture = Http().bindAndHandle(httpServer.scWebAppRoutes, httpServer.httpServerConfig.host, httpServer.httpServerConfig.port)
    bindFuture.foreach { binding ⇒
      serverBinding = binding
      actorSystem.log.info("shadowcloud server running on {}", binding.localAddress)
    }
  }

  override def shutdown(): Unit = {
    import scala.concurrent.duration._
    if (serverBinding != null) Await.ready(serverBinding.unbind(), 10 seconds)
  }
}
