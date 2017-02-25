package com.karasiq.shadowcloud.server.http

import java.nio.file.Files

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.{Directive1, HttpApp, Route}
import akka.http.scaladsl.settings.ServerSettings
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.{ByteString, Timeout}
import com.karasiq.shadowcloud.actors.RegionSupervisor.{AddRegion, AddStorage, RegisterStorage}
import com.karasiq.shadowcloud.actors.messages.RegionEnvelope
import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, RegionDispatcher, RegionSupervisor}
import com.karasiq.shadowcloud.index.diffs.{FileVersions, FolderIndexDiff}
import com.karasiq.shadowcloud.index.{Chunk, File, Path}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams._
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object Main extends HttpApp with App with PredefinedToResponseMarshallers {
  // Actor system
  implicit val actorSystem = ActorSystem("shadowcloud-server")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val executionContext = actorSystem.dispatcher

  // Region supervisor
  val regionSupervisor = actorSystem.actorOf(RegionSupervisor.props(), "regions")
  regionSupervisor ! AddRegion("testRegion")
  regionSupervisor ! AddStorage("testStorage", StorageProps.fromDirectory(Files.createTempDirectory("scl-http-test")))
  regionSupervisor ! RegisterStorage("testRegion", "testStorage")

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected def route: Route = {
    post {
      (extractRequestEntity & extractPath) { (entity, path) ⇒
        val future = entity.dataBytes
          .runWith(writeFile("testRegion", path))
          .map(file ⇒ Utils.toHexString(file.checksum.encryptedHash))
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
  private[this] def findFile(regionId: String, path: Path): Future[File] = {
    implicit val timeout = Timeout(10 seconds)
    (regionSupervisor ? RegionEnvelope(regionId, RegionDispatcher.GetFiles(path)))
      .mapTo[RegionDispatcher.GetFiles.Status]
      .flatMap {
        case RegionDispatcher.GetFiles.Success(_, files) ⇒
          Future.successful(FileVersions.mostRecent(files))

        case RegionDispatcher.GetFiles.Failure(_, error) ⇒
          Future.failed(error)
      }
  }

  private[this] def getFile(regionId: String, path: Path): Source[ByteString, NotUsed] = { // TODO: Byte ranges
    implicit val timeout = Timeout(10 seconds)
    val readChunks = Flow[Chunk]
      .mapAsync(4)(chunk ⇒ regionSupervisor ? RegionEnvelope(regionId, ChunkIODispatcher.ReadChunk(chunk)))
      .flatMapMerge(4, {
        case ChunkIODispatcher.ReadChunk.Success(chunk, source) ⇒
          source
            .via(ByteStringConcat())
            .map(bytes ⇒ chunk.copy(data = chunk.data.copy(encrypted = bytes)))

        case ChunkIODispatcher.ReadChunk.Failure(_, error) ⇒
          Source.failed(error)
      })

    Source.fromFuture(findFile(regionId, path))
      .mapConcat(_.chunks.toVector)
      .via(readChunks)
      .via(ChunkDecryptor())
      .via(ChunkVerifier())
      .map(_.data.plain)
  }

  private[this] def writeFile(regionId: String, path: Path): Sink[ByteString, Future[File]] = {
    // TODO: Actor publisher
    implicit val timeout = Timeout(1 hour)
    Flow.fromGraph(FileSplitter())
      .via(ChunkEncryptor())
      .mapAsync(1)(chunk ⇒ regionSupervisor ? RegionEnvelope(regionId, ChunkIODispatcher.WriteChunk(chunk)))
      .flatMapConcat {
        case ChunkIODispatcher.WriteChunk.Success(_, chunk) ⇒
          Source.single(chunk)

        case ChunkIODispatcher.WriteChunk.Failure(_, error) ⇒
          Source.failed(error)
      }
      .viaMat(FileIndexer())(Keep.right)
      .to(Sink.ignore)
      .mapMaterializedValue { future ⇒
        val fileFuture = findFile(regionId, path)
          .flatMap(file ⇒ future.map(result ⇒ file.copy(lastModified = Utils.timestamp, checksum = result.checksum, chunks = result.chunks)))
          .recoverWith(PartialFunction(_ ⇒ future.map(result ⇒ File(path, Utils.timestamp, Utils.timestamp, result.checksum, result.chunks))))
        fileFuture.foreach { file ⇒
          regionSupervisor ! RegionEnvelope(regionId, RegionDispatcher.WriteIndex(FolderIndexDiff.createFiles(file)))
        }
        fileFuture
      }
  }

  // -----------------------------------------------------------------------
  // Start server
  // -----------------------------------------------------------------------
  startServer("0.0.0.0", 9000, ServerSettings(actorSystem), actorSystem)
}
