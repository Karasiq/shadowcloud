package com.karasiq.shadowcloud.server.http

import java.nio.file.{Files, Paths}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{Directive1, HttpApp, Route}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.actors.RegionSupervisor.{AddRegion, AddStorage, RegisterStorage}
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams._

import scala.language.postfixOps

object Main extends HttpApp with App with PredefinedToResponseMarshallers {
  // Actor system
  implicit val actorSystem = ActorSystem("shadowcloud-server")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val config = AppConfig(actorSystem)
  val chunkProcessing = ChunkProcessing(config)

  // Region supervisor
  val tempDirectory = sys.props.get("shadowcloud.temp-storage-dir")
    .map(Paths.get(_))
    .getOrElse(Files.createTempDirectory("scl-temp-storage"))
  val regionSupervisor = actorSystem.actorOf(RegionSupervisor.props, "regions")
  regionSupervisor ! AddRegion("testRegion")
  regionSupervisor ! AddStorage("testStorage", StorageProps.fromDirectory(tempDirectory))
  regionSupervisor ! RegisterStorage("testRegion", "testStorage")
  val regionStreams = RegionStreams(regionSupervisor, config.parallelism)
  val fileStreams = FileStreams(regionStreams, chunkProcessing)

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected def route: Route = {
    post {
      (extractRequestEntity & extractPath) { (entity, path) ⇒
        val future = entity.withoutSizeLimit().dataBytes
          .runWith(fileStreams.write("testRegion", path))
          .map(_.chunks.mkString("\r\n"))
        complete(future)
      }
    } ~
    get {
      (pathPrefix("file") & extractPath) { path ⇒
        val stream = fileStreams.read("testRegion", path)
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
