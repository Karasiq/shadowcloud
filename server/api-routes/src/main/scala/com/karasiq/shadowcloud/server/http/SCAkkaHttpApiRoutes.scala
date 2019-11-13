package com.karasiq.shadowcloud.server.http

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ByteRange, Range}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.util.ByteString
import autowire.Core.Request
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.{SCApiUtils, ShadowCloudApi}
import com.karasiq.shadowcloud.api.jvm.SCDefaultApiServer
import com.karasiq.shadowcloud.server.http.api.ShadowCloudApiImpl
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

private[server] trait SCAkkaHttpApiRoutes { self: Directives ⇒
  protected val sc: ShadowCloudExtension

  private[http] object SCApiInternals {
    private[this] implicit val executionContext: ExecutionContext = sc.implicits.executionContext
    val apiServer = SCDefaultApiServer

    val apiEncoding = apiServer.encoding
    import apiEncoding.implicits._

    val apiRouter = apiServer.route[ShadowCloudApi](new ShadowCloudApiImpl(sc))
    type RequestT = apiServer.Request

    val apiMediaType = {
      MediaType.parse(apiServer.payloadContentType)
        .getOrElse(sys.error(s"Invalid content type: ${apiServer.payloadContentType}"))
    }

    val apiContentType = ContentType(apiMediaType, () ⇒ HttpCharsets.`UTF-8`)
  }

  import SCApiInternals._
  import apiEncoding.implicits._

  private[http] object SCApiMarshallers {
    implicit def implicitSCEntityMarshaller[T: apiEncoding.Encoder]: Marshaller[T, ByteString] =
      Marshaller.withFixedContentType(apiContentType)(apiEncoding.encode[T])

    implicit def implicitSCEntityUnmarshaller[T: apiEncoding.Decoder]: Unmarshaller[ByteString, T] =
      Unmarshaller.strict(apiEncoding.decode[T])
  }

  private[http] object SCApiDirectives {
    def extractChunkRanges(fullSize: Long): Directive1[ChunkRanges.RangeList] = headerValueByType[Range](()).map { range ⇒
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

    val validateRequestedWith: Directive0 = validateHeader("X-Requested-With", _ == SCApiUtils.RequestedWith)

    val extractApiRequest: Directive1[RequestT] = {
      def extractAutowireRequest(timeout: FiniteDuration = 5 seconds): Directive1[RequestT] = {
        extractUnmatchedPath.flatMap {
          case Uri.Path.Slash(path) ⇒
            extractStrictEntity(timeout).map { entity ⇒
              Request(path.toString().split("/"), SCApiInternals.apiServer.decodePayload(entity.data))
            }

          case _ ⇒
            reject
        }
      }

      def extractValidRequest: Directive1[RequestT] = {
        extractAutowireRequest()
          .filter(apiRouter.isDefinedAt, MalformedRequestContentRejection("Invalid api request", new IllegalArgumentException))
      }

      // checkSameOrigin(HttpOriginRange(HttpOrigin("http://localhost:9000"), HttpOrigin("http://127.0.0.1:9000"))) &
      // validateHeader("Content-Type", apiServer.payloadContentType) &

      validateRequestedWith &
        validateContentType(apiMediaType) &
        validateHeader("SC-Accept", _ == apiServer.payloadContentType) &
        extractValidRequest
    }

    def executeApiRequest(request: RequestT): Route = {
      onSuccess(apiRouter(request)) { result ⇒
        complete(HttpEntity(SCApiInternals.apiContentType, result))
      }
    }
  }

  def scApiRoute: Route = (post & pathPrefix("api") & SCApiDirectives.extractApiRequest) { request ⇒
    SCApiDirectives.executeApiRequest(request)
  }
}
