package com.karasiq.shadowcloud.server.http

import java.nio.file.{Files, Paths}

import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{Directive1, HttpApp, Route}
import akka.http.scaladsl.settings.ServerSettings

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage.props.StorageProps

object Main extends HttpApp with App with PredefinedToResponseMarshallers {
  // Actor system
  implicit val actorSystem = ActorSystem("shadowcloud-server")
  val sc = ShadowCloud(actorSystem)
  import sc.implicits._
  import sc.ops.supervisor

  // Test storage
  val tempDirectory = sys.props.get("shadowcloud.temp-storage-dir")
    .map(Paths.get(_))
    .getOrElse(Files.createTempDirectory("scl-temp-storage"))
  supervisor.addRegion("testRegion", sc.regionConfig("testRegion"))
  supervisor.addStorage("testStorage", StorageProps.fromDirectory(tempDirectory))
  supervisor.register("testRegion", "testStorage")

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected def routes: Route = {
    post {
      (extractRequestEntity & extractPath) { (entity, path) ⇒
        val future = entity.withoutSizeLimit().dataBytes
          .runWith(sc.streams.file.write("testRegion", path))
          .map(_.chunks.mkString("\r\n"))
        complete(future)
      }
    } ~
    get {
      (pathPrefix("file") & extractPath) { path ⇒
        val stream = sc.streams.file.readMostRecent("testRegion", path)
        complete(HttpEntity(ContentTypes.`application/octet-stream`, stream))
      } ~
      getStaticFiles
    }
  }

  // -----------------------------------------------------------------------
  // Directives
  // -----------------------------------------------------------------------
  private[this] def extractPath: Directive1[Path] = {
    path(Remaining).map(Path.fromString)
  }

  private[this] def getStaticFiles: Route = {
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
  startServer("0.0.0.0", 9000, ServerSettings(actorSystem), actorSystem)
}
