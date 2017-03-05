package com.karasiq.shadowcloud.server.http

import java.nio.file.Files

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{Directive1, HttpApp, Route}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.actors.RegionSupervisor.{AddRegion, AddStorage, RegisterStorage}
import com.karasiq.shadowcloud.index.{File, Path}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams._
import com.karasiq.shadowcloud.utils.{HexString, MemorySize}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends HttpApp with App with PredefinedToResponseMarshallers {
  // Actor system
  implicit val actorSystem = ActorSystem("shadowcloud-server")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher
  val chunkProcessing = ChunkProcessing(actorSystem)

  // Region supervisor
  val regionSupervisor = actorSystem.actorOf(RegionSupervisor.props, "regions")
  regionSupervisor ! AddRegion("testRegion")
  regionSupervisor ! AddStorage("testStorage", StorageProps.fromDirectory(Files.createTempDirectory("scl-http-test")))
  regionSupervisor ! RegisterStorage("testRegion", "testStorage")
  val regionStreams = RegionStreams(regionSupervisor)

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected def route: Route = {
    post {
      (extractRequestEntity & extractPath) { (entity, path) ⇒
        val future = entity.dataBytes
          .runWith(writeFile("testRegion", path))
          .map(file ⇒ HexString.encode(file.checksum.encryptedHash))
        complete(future)
      }
    } ~
    get {
      (pathPrefix("file") & extractPath) { path ⇒
        complete(HttpEntity(ContentTypes.`application/octet-stream`, getFile("testRegion", path)))
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
  // Actions
  // -----------------------------------------------------------------------
  private[this] def getFile(regionId: String, path: Path): Source[ByteString, NotUsed] = { // TODO: Byte ranges
    implicit val timeout = Timeout(10 seconds)
    Source.single((regionId, path))
      .via(regionStreams.findFile)
      .mapConcat(_.chunks.toVector)
      .map((regionId, _))
      .via(regionStreams.readChunks)
      .via(chunkProcessing.afterRead)
      .map(_.data.plain)
  }

  private[this] def writeFile(regionId: String, path: Path): Sink[ByteString, Future[File]] = {
    // TODO: Actor publisher
    implicit val timeout = Timeout(1 hour)
    Flow.fromGraph(ChunkSplitter(MemorySize.MB * 8))
      .via(chunkProcessing.beforeWrite())
      .map((regionId, _))
      .via(regionStreams.writeChunks)
      .toMat(chunkProcessing.index())(Keep.right)
      .mapMaterializedValue { future ⇒
        Source.fromFuture(future)
          .map((regionId, path, _))
          .via(regionStreams.addFile)
          .runWith(Sink.head)
      }
  }

  // -----------------------------------------------------------------------
  // Start server
  // -----------------------------------------------------------------------
  startServer("0.0.0.0", 9000, ServerSettings(actorSystem), actorSystem)
}
