package com.karasiq.shadowcloud.server.http

import java.nio.file.{Files, Paths}
import java.util.UUID

import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Content-Range`, `Last-Modified`}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.index.{Chunk, File, Path}
import com.karasiq.shadowcloud.index.diffs.FileVersions
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges.RangeList
import com.karasiq.shadowcloud.utils.{MemorySize, Utils}

object HttpServerMain extends HttpApp with App with PredefinedToResponseMarshallers with SCAkkaHttpApiServer {
  import apiInternals.apiEncoding.implicits._

  // Actor system
  private[this] val actorSystem: ActorSystem = ActorSystem("shadowcloud-server")
  protected val sc = ShadowCloud(actorSystem)

  import sc.implicits._
  import sc.ops.supervisor

  // -----------------------------------------------------------------------
  // Config
  // -----------------------------------------------------------------------
  private[this] object settings extends ConfigImplicits {
    val config = sc.config.rootConfig.getConfig("http-server")
    val useMultipartByteRanges = config.getBoolean("use-multipart-byte-ranges")
  }

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected val routes: Route = {
    encodeResponse(scApiRoute) ~
    (post & apiDirectives.validateRequestedWith) {
      (path("upload" / Segment / SCPath) & extractRequestEntity) { (regionId, path, entity) ⇒
        val future = entity.withoutSizeLimit().dataBytes
          .via(sc.streams.metadata.writeFileAndMetadata(regionId, path))
          .map(_._1)
          .runWith(Sink.head)

        onSuccess(future) { file ⇒
          complete(apiInternals.apiEncoding.encodeFile(file))
        }
      }
    } ~
    get {
      path("download" / Segment / SCPath / Segment) { (regionId, path, _) ⇒
        fileDirectives.findFile(regionId, path) { file ⇒
          fileDirectives.provideTimestamp(file)(fileDirectives.readFile(regionId, file))
        }
      } ~
      staticFilesRoute
    }
  }

  // -----------------------------------------------------------------------
  // Directives
  // -----------------------------------------------------------------------
  private[this] object SCPath extends PathMatcher1[Path] {
    def apply(path: Uri.Path): PathMatcher.Matching[Tuple1[Path]] = path match {
      case Uri.Path.Segment(segment, tail) ⇒
        try {
          val path = apiInternals.apiEncoding.decodePath(SCApiEncoding.toBinary(segment))
          Matched(tail, Tuple1(path))
        } catch { case NonFatal(_) ⇒
          Unmatched
        }

      case _ ⇒
        Unmatched
    }
  }

  private[this] object fileDirectives {
    def findFiles(regionId: RegionId, path: Path): Directive1[Set[File]] = {
      onSuccess(sc.ops.region.getFiles(regionId, path))
    }

    def findFile(regionId: RegionId, path: Path): Directive1[File] = {
      findFiles(regionId, path).flatMap { files ⇒
        parameter("file-id")
          .map(id ⇒ FileVersions.withId(UUID.fromString(id), files))
          .recover(_ ⇒ provide(FileVersions.mostRecent(files)))
      }
    }

    def provideTimestamp(file: File): Directive0 = {
      respondWithHeader(`Last-Modified`(DateTime(file.timestamp.lastModified)))
    }

    def readChunkStream(regionId: RegionId, chunks: Seq[Chunk], fileName: String = ""): Route = {
      val contentType = try {
        ContentType(MediaTypes.forExtension(Utils.getFileExtensionLowerCase(fileName)), () ⇒ HttpCharsets.`UTF-8`)
      } catch { case NonFatal(_) ⇒
        ContentTypes.NoContentType
        // ContentTypes.`application/octet-stream`
      }

      def createEntity(dataStream: Source[ByteString, NotUsed], contentLength: Long) = {
        if (contentLength > 0) {
          HttpEntity.Default(contentType, contentLength, dataStream)
        } else {
          HttpEntity.empty(contentType)
        }
      }

      val chunkStreamSize = chunks.map(_.checksum.size).sum

      def toContentRange(range: ChunkRanges.Range) = {
        val maxIndex = math.max(0L, chunkStreamSize - 1)
        val start = math.min(maxIndex, math.max(0L, range.start))
        val end = math.min(maxIndex, math.max(start, range.end - 1))
        ContentRange(start, end, Some(chunkStreamSize).filter(_ > end))
      }

      (apiDirectives.extractChunkRanges(chunkStreamSize) & extractLog) { (ranges, log) ⇒
        val fullRangesSize = ranges.size
        ranges match {
          case ranges if !settings.useMultipartByteRanges || ranges.isOverlapping ⇒
            log.info("Byte ranges of size {} requested: {}", MemorySize(fullRangesSize), ranges)
            val stream = sc.streams.file.readChunkStreamRanged(regionId, chunks, ranges)
            respondWithHeader(`Content-Range`(toContentRange(ranges.toRange))) {
              complete(StatusCodes.PartialContent, createEntity(stream, fullRangesSize))
            }

          case ranges ⇒
            log.info("Multipart byte ranges of size {} requested: {}", MemorySize(fullRangesSize), ranges)
            val partsStream = Source.fromIterator(() ⇒ ranges.ranges.iterator).map { range ⇒
              val dataStream = sc.streams.file.readChunkStreamRanged(regionId, chunks, RangeList(range))
              Multipart.ByteRanges.BodyPart(toContentRange(range), createEntity(dataStream, range.size))
            }
            complete(StatusCodes.PartialContent, Multipart.ByteRanges(partsStream))
        }
      } ~ {
        val stream = sc.streams.file.readChunkStream(regionId, chunks)
        complete(HttpEntity(contentType, chunkStreamSize, stream))
      }
    }

    def readFile(regionId: RegionId, file: File): Route = {
      readChunkStream(regionId, file.chunks, file.path.name)
    }
  }

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
  // Pre-start
  // -----------------------------------------------------------------------
  sc.keys.getOrGenerateChain().foreach { keyChain ⇒
    println(s"Key chain initialized: $keyChain")
  }

  val tempDirectory = sys.props.get("shadowcloud.temp-storage-dir")
    .map(Paths.get(_))
    .getOrElse(Files.createTempDirectory("scl-temp-storage"))
  supervisor.addRegion("testRegion", sc.configs.regionConfig("testRegion"))
  supervisor.addStorage("testStorage", StorageProps.fromDirectory(tempDirectory))
  supervisor.register("testRegion", "testStorage")

  import scala.concurrent.duration._
  actorSystem.scheduler.scheduleOnce(30 seconds) {
    sc.ops.region.collectGarbage("testRegion", delete = false)
  }

  // -----------------------------------------------------------------------
  // Start server
  // -----------------------------------------------------------------------
  startServer("0.0.0.0", 9000, ServerSettings(actorSystem), actorSystem)
}
