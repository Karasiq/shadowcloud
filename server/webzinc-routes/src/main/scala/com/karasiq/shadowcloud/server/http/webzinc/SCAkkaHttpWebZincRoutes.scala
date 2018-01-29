package com.karasiq.shadowcloud.server.http.webzinc

import java.net.URI

import scala.concurrent.Future

import akka.NotUsed
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.shadowcloud.server.http.{SCAkkaHttpApiRoutes, SCAkkaHttpFileRoutes}
import com.karasiq.webzinc.WebResourceInliner
import com.karasiq.webzinc.impl.htmlunit.HtmlUnitWebResourceFetcher
import com.karasiq.webzinc.utils.WebZincUtils

// WebZinc extension
trait SCAkkaHttpWebZincRoutes { self: SCAkkaHttpFileRoutes with SCAkkaHttpApiRoutes with Directives ⇒
  lazy val scWebZincRoute: Route = {
    (post & SCApiDirectives.validateRequestedWith) {
      (path("save_page" / Segment / SCPath) & parameter("url")) { (regionId, path, url) ⇒
        onSuccess(WebZincContext.fetchPage(url)) { case (name, stream) ⇒
          SCFileDirectives.writeFile(regionId, path / name, stream)
        }
      }
    }
  }

  protected object WebZincContext {
    type PageFuture = Future[(String, Source[ByteString, NotUsed])]

    import sc.implicits.{actorSystem, materializer}
    private[this] implicit val dispatcher = actorSystem.dispatchers.lookup(SCWebZinc.dispatcherId)
    private[this] val akkaHttp = Http(actorSystem)

    private[this] val fetcher = HtmlUnitWebResourceFetcher() // TODO: https://github.com/akka/akka-http/issues/86
    private[this] val inliner = WebResourceInliner()

    def fetchWebPage(url: String): PageFuture = {
      val pageFuture = fetcher.getWebPage(url).flatMap((inliner.inline _).tupled)
      pageFuture.map(page ⇒ (WebZincUtils.getFileName(page), Source.single(page.data)))
    }

    def fetchHttpFile(url: String): PageFuture = {
      akkaHttp.singleRequest(HttpRequest(uri = url)).map { response ⇒
        val fileName = response.header[`Content-Disposition`]
          .flatMap(_.params.get("filename"))
          .orElse(new URI(url).getPath.split('/').lastOption)
          .filter(_.nonEmpty)
          .getOrElse(url)

        (fileName, response.entity/*.withoutSizeLimit()*/.dataBytes.mapMaterializedValue(_ ⇒ NotUsed))
      }
    }

    def fetchPage(url: String): PageFuture = {
      fetchWebPage(url).recoverWith { case _ ⇒ fetchHttpFile(url) }
    }
  }
}
