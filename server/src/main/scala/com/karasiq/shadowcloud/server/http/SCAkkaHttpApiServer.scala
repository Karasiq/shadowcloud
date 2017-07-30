package com.karasiq.shadowcloud.server.http

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.http.scaladsl.model.{ContentTypes, Uri}
import akka.http.scaladsl.server._
import autowire.Core.Request
import play.api.libs.json.{Json, JsValue, Writes}

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.{SCApiEncoding, ShadowCloudApi}
import com.karasiq.shadowcloud.api.jvm.JsonApiServer
import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.server.http.api.ShadowCloudApiImpl

private[server] trait SCAkkaHttpApiServer { self: Directives ⇒
  import SCJsonEncoders._
  protected val sc: ShadowCloudExtension

  private[this] object scApiInternal {
    private[this] implicit val executionContext: ExecutionContext = sc.implicits.executionContext
    val apiServer = JsonApiServer
    val apiEncoding = SCApiEncoding.default
    val apiRouter = apiServer.route[ShadowCloudApi](new ShadowCloudApiImpl(sc))
    type ApiRequest = apiServer.Request
  }

  import scApiInternal._

  protected object scApiDirectives {
    private[this] def validateHeader(name: String, expectedValue: String): Directive0 = {
      headerValueByName(name).flatMap { value ⇒
        if(value == expectedValue) pass
        else reject(UnacceptedResponseContentTypeRejection(Set(ContentNegotiator.Alternative(ContentTypes.`application/json`))))
      }
    }

    private[this] def extractAutowireRequest(timeout: FiniteDuration = 5 seconds): Directive1[ApiRequest] = {
      extractUnmatchedPath.flatMap {
        case Uri.Path.Slash(path) ⇒
          extractStrictEntity(timeout).map { entity ⇒
            val entityJson = Json.fromJson[Map[String, JsValue]](Json.parse(entity.data.utf8String)).get
            Request(path.toString().split("/"), entityJson)
          }

        case _ ⇒
          reject
      }
    }

    private[this] def extractValidRequest: Directive1[ApiRequest] = extractAutowireRequest().flatMap { request ⇒
      validate(apiRouter.isDefinedAt(request), "Invalid request").tflatMap(_ ⇒ provide(request))
    }

    private[SCAkkaHttpApiServer] def extractApiRequest: Directive1[ApiRequest] = {
      validateHeader("Content-Type", "application/json") & validateHeader("Accept", "application/json") & extractValidRequest
    }

    private[SCAkkaHttpApiServer] def executeApiRequest(request: ApiRequest): Route = {
      onSuccess(apiRouter(request)) { result ⇒
        encodeApiResult(result)
      }
    }

    def extractFilePath: Directive1[Path] = {
      parameter("path").map(apiEncoding.decodePath)
    }

    def encodeApiResult[R: Writes](value: R): Route = {
      complete(Json.stringify(apiServer.write(value)))
    }
  }

  def scApiRoute: Route = (post & path("api") & scApiDirectives.extractApiRequest) { request ⇒
    scApiDirectives.executeApiRequest(request)
  }
}
