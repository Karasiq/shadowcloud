package com.karasiq.shadowcloud.server.http

import java.util.UUID

import scala.util.control.NonFatal

import akka.NotUsed
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Content-Range`, `Last-Modified`}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.stream.scaladsl.Source
import akka.util.ByteString

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.{Chunk, File, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges.RangeList
import com.karasiq.shadowcloud.utils.Utils

trait SCAkkaHttpFileServer { self: SCAkkaHttpApiServer with SCHttpServerSettings with Directives ⇒
  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  def scFileRoute: Route = {
    (post & SCApiDirectives.validateRequestedWith) {
      (path("upload" / Segment / SCPath) & extractRequestEntity) { (regionId, path, entity) ⇒
        val dataStream = entity.withoutSizeLimit()
          .dataBytes
          .mapMaterializedValue(_ ⇒ NotUsed)

        SCFileDirectives.writeFile(regionId, path, dataStream)
      }
    } ~
    get {
      path("download" / Segment / SCPath / Segment) { (regionId, path, _) ⇒
        SCFileDirectives.findFile(regionId, path) { file ⇒
          SCFileDirectives.provideTimestamp(file)(SCFileDirectives.readFile(regionId, file))
        }
      }
    }
  }

  // -----------------------------------------------------------------------
  // Directives
  // -----------------------------------------------------------------------
  private[http] object SCPath extends PathMatcher1[Path] {
    def apply(path: Uri.Path): PathMatcher.Matching[Tuple1[Path]] = path match {
      case Uri.Path.Segment(segment, tail) ⇒
        try {
          val path = SCApiInternals.apiEncoding.decodePath(SCApiEncoding.toBinary(segment))
          Matched(tail, Tuple1(path))
        } catch { case NonFatal(_) ⇒
          Unmatched
        }

      case _ ⇒
        Unmatched
    }
  }

  private[http] object SCFileDirectives {
    def findFiles(regionId: RegionId, path: Path): Directive1[Set[File]] = {
      parameter("scope")
        .map(scope ⇒ SCApiInternals.apiEncoding.decodeScope(SCApiEncoding.toBinary(scope)))
        .recover(_ ⇒ provide(IndexScope.default: IndexScope))
        .flatMap(scope ⇒ onSuccess(sc.ops.region.getFiles(regionId, path, scope)))
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

    def writeFile(regionId: RegionId, path: Path, stream: Source[ByteString, NotUsed]): Route = {
      val resultStream = stream
        .via(sc.streams.metadata.writeFileAndMetadata(regionId, path))
        .log("uploaded-files")
        .map(_._1)
        .map(SCApiInternals.apiEncoding.encodeFile)

      complete(StatusCodes.OK, HttpEntity(SCApiInternals.apiContentType, resultStream))
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

      (SCApiDirectives.extractChunkRanges(chunkStreamSize) & extractLog) { (ranges, log) ⇒
        val fullRangesSize = ranges.size
        ranges match {
          case ranges if !SCHttpSettings.useMultipartByteRanges || ranges.isOverlapping ⇒
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
}
