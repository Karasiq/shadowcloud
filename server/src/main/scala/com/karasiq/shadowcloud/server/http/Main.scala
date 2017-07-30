package com.karasiq.shadowcloud.server.http

import java.nio.file.{Files, Paths}

import scala.language.postfixOps

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{HttpApp, Route}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.scaladsl.Sink

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.storage.props.StorageProps

object Main extends HttpApp with App with PredefinedToResponseMarshallers with SCAkkaHttpApiServer {
  import SCJsonEncoders._

  // Actor system
  private[this] val actorSystem: ActorSystem = ActorSystem("shadowcloud-server")
  protected val sc = ShadowCloud(actorSystem)

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
    encodeResponse(scApiRoute) ~
    post {
      (path("upload" / Segment) & scApiDirectives.extractFilePath & extractRequestEntity) { (regionId, path, entity) ⇒
        val future = entity.withoutSizeLimit().dataBytes
          .via(sc.streams.metadata.writeFileAndMetadata(regionId, path))
          .map(_._1)
          .runWith(Sink.head)
        onSuccess(future)(scApiDirectives.encodeApiResult)
      }
    } ~
    get {
      (path("download" / Segment / Remaining) & scApiDirectives.extractFilePath) { (regionId, _, path) ⇒
        val stream = sc.streams.file.readMostRecent(regionId, path)
        complete(HttpEntity(ContentTypes.`application/octet-stream`, stream))
      } ~
      staticFilesRoute
    }
  }

  // -----------------------------------------------------------------------
  // Directives
  // -----------------------------------------------------------------------
  private[this] def staticFilesRoute: Route = {
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
