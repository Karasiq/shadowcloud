package com.karasiq.shadowcloud.server.http.webzinc

import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.server.http.{SCAkkaHttpApiRoutes, SCAkkaHttpFileRoutes}
import com.karasiq.webzinc.fetcher.WebResourceFetcher
import com.karasiq.webzinc.inliner.WebResourceInliner

// WebZinc extension
trait SCAkkaHttpWebZincRoutes { self: SCAkkaHttpFileRoutes with SCAkkaHttpApiRoutes with Directives ⇒
  lazy val scWebZincRoute: Route = {
    import sc.implicits.materializer
    implicit val dispatcher = sc.implicits.actorSystem.dispatchers.lookup(SCWebZinc.dispatcherId)
    val fetcher = WebResourceFetcher()
    val inliner = WebResourceInliner()

    (post & SCApiDirectives.validateRequestedWith) {
      (path("save_page" / Segment / SCPath) & parameter("url")) { (regionId, path, url) ⇒
        val pageFuture = fetcher.getWebPage(url).flatMap((inliner.inline _).tupled)
        onSuccess(pageFuture) { page ⇒
          SCFileDirectives.writeFile(regionId, path / s"${page.title} [${Integer.toHexString(url.hashCode)}].html", Source.single(page.data))
        }
      }
    }
  }
}
