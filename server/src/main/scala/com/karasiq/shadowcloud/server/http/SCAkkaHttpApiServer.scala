package com.karasiq.shadowcloud.server.http

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ByteRange, Range}
import akka.http.scaladsl.server._
import autowire.Core.Request
import play.api.libs.json.Json

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.{SCApiUtils, ShadowCloudApi}
import com.karasiq.shadowcloud.api.jvm.SCDefaultApiServer
import com.karasiq.shadowcloud.server.http.api.ShadowCloudApiImpl
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

private[server] trait SCAkkaHttpApiServer { self: Directives ⇒
  protected val sc: ShadowCloudExtension

  protected object apiInternals {
    private[this] implicit val executionContext: ExecutionContext = sc.implicits.executionContext
    val apiServer = SCDefaultApiServer

    val apiEncoding = apiServer.encoding
    import apiEncoding.implicits._

    val apiRouter = apiServer.route[ShadowCloudApi](new ShadowCloudApiImpl(sc))
    type RequestT = apiServer.Request
  }

  import apiInternals._
  import apiEncoding.implicits._

  protected object apiDirectives {
    def extractChunkRanges(fullSize: Long) = headerValueByType[Range](()).map { range ⇒
      ChunkRanges.RangeList(range.ranges.map {
        case ByteRange.FromOffset(offset) ⇒
          ChunkRanges.Range(math.min(fullSize, offset), fullSize)

        case ByteRange.Slice(first, last) ⇒
          ChunkRanges.Range(first, math.min(fullSize - 1, last) + 1)

        case ByteRange.Suffix(length) ⇒
          ChunkRanges.Range(math.max(0L, fullSize - length), fullSize)
      })
    }

    def validateContentType(expectedValue: MediaType): Directive0 = {
      extract(_.request.entity.contentType)
        .require(_.mediaType == expectedValue, UnsupportedRequestContentTypeRejection(Set(ContentTypeRange(expectedValue))))
    }

    def validateHeader(name: String, func: String ⇒ Boolean): Directive0 = {
      headerValueByName(name).require(func, MalformedHeaderRejection(name, "Invalid header value"))
    }

    val validateRequestedWith = {
      validateHeader("X-Requested-With", _ == SCApiUtils.requestedWith)
    }

    val extractApiRequest: Directive1[RequestT] = {
      def extractAutowireRequest(timeout: FiniteDuration = 5 seconds): Directive1[RequestT] = {
        extractUnmatchedPath.flatMap {
          case Uri.Path.Slash(path) ⇒
            extractStrictEntity(timeout).map { entity ⇒
              Request(path.toString().split("/"), apiInternals.apiServer.decodePayload(entity.data))
            }

          case _ ⇒
            reject
        }
      }

      def extractValidRequest: Directive1[RequestT] = {
        extractAutowireRequest()
          .filter(apiRouter.isDefinedAt, MalformedRequestContentRejection("Invalid api request", new IllegalArgumentException))
      }

      val contentType = {
        MediaType.parse(apiServer.payloadContentType).right
          .getOrElse(sys.error(s"Invalid content type: ${apiServer.payloadContentType}"))
      }

      // checkSameOrigin(HttpOriginRange(HttpOrigin("http://localhost:9000"), HttpOrigin("http://127.0.0.1:9000"))) &
      // validateHeader("Content-Type", apiServer.payloadContentType) &

      validateRequestedWith &
        validateContentType(contentType) &
        validateHeader("SC-Accept", _ == apiServer.payloadContentType) &
        extractValidRequest
    }

    def executeApiRequest(request: RequestT): Route = {
      onSuccess(apiRouter(request)) { result ⇒
        complete(Json.stringify(apiServer.write(result)))
      }
    }
  }

  def scApiRoute: Route = (post & pathPrefix("api") & apiDirectives.extractApiRequest) { request ⇒
    apiDirectives.executeApiRequest(request)
  }
}
