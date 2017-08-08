package com.karasiq.shadowcloud.server.http

import java.nio.file.{Files, Paths}

import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{HttpApp, PathMatcher, PathMatcher1, Route}
import akka.http.scaladsl.server.PathMatcher.{Matched, Unmatched}
import akka.http.scaladsl.settings.ServerSettings
import akka.stream.scaladsl.Sink
import org.apache.commons.io.FilenameUtils

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage.props.StorageProps

object HttpServerMain extends HttpApp with App with PredefinedToResponseMarshallers with SCAkkaHttpApiServer {
  import scApiInternal.apiEncoding.implicits._

  // Actor system
  private[this] val actorSystem: ActorSystem = ActorSystem("shadowcloud-server")
  protected val sc = ShadowCloud(actorSystem)

  import sc.implicits._
  import sc.ops.supervisor

  // -----------------------------------------------------------------------
  // Route
  // -----------------------------------------------------------------------
  protected val routes: Route = {
    encodeResponse(scApiRoute) ~
    (post & scApiDirectives.validateRequestedWith) {
      (path("upload" / Segment / SCPath) & extractRequestEntity) { (regionId, path, entity) ⇒
        val future = entity.withoutSizeLimit().dataBytes
          .via(sc.streams.metadata.writeFileAndMetadata(regionId, path))
          .map(_._1)
          .runWith(Sink.head)

        onSuccess(future) { file ⇒
          complete(scApiInternal.apiEncoding.encodeFile(file))
        }
      }
    } ~
    get {
      path("download" / Segment / SCPath / Segment) { (regionId, path, fileName) ⇒
        val stream = sc.streams.file.readMostRecent(regionId, path)
        val contentType = try {
          ContentType(MediaTypes.forExtension(FilenameUtils.getExtension(fileName)), () ⇒ HttpCharsets.`UTF-8`)
        } catch { case NonFatal(_) ⇒
          ContentTypes.`application/octet-stream`
        }
        complete(HttpEntity(contentType, stream))
      } ~
      staticFilesRoute
    }
  }

  // -----------------------------------------------------------------------
  // Directives
  // -----------------------------------------------------------------------
  object SCPath extends PathMatcher1[Path] {
    def apply(path: Uri.Path): PathMatcher.Matching[Tuple1[Path]] = path match {
      case Uri.Path.Segment(segment, tail) ⇒
        try {
          val path = scApiInternal.apiEncoding.decodePath(SCApiEncoding.toBinary(segment))
          Matched(tail, Tuple1(path))
        } catch { case NonFatal(_) ⇒
          Unmatched
        }

      case _ ⇒
        Unmatched
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
    sc.ops.region.collectGarbage("testRegion", delete = true)
  }

  // -----------------------------------------------------------------------
  // Start server
  // -----------------------------------------------------------------------
  startServer("0.0.0.0", 9000, ServerSettings(actorSystem), actorSystem)
}
