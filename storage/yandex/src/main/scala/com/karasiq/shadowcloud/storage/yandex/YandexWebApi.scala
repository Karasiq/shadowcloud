package com.karasiq.shadowcloud.storage.yandex

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.yandex.YandexWebApi.{YandexSession, YandexUsedSpace}
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

private[yandex] object YandexWebApi {

  case class YandexSession(session: JsObject, cookies: Seq[HttpCookie], sessionKey: String) {
    def headers: List[HttpHeader] = List[HttpHeader](Cookie(cookies.map(_.pair()).toList))
  }

  case class YandexUsedSpace(used: Long, free: Long, limit: Long)

  object YandexUsedSpace {
    implicit val jsonReads = Json.reads[YandexUsedSpace]
  }

}

private[yandex] class YandexWebApi(
    solveCaptcha: String ⇒ Future[String] = _ ⇒ Future.failed(new IllegalStateException("Unimplemented")),
    passChallenge: String ⇒ Future[Done] = _ ⇒ Future.failed(new IllegalStateException("Unimplemented"))
)(implicit as: ActorSystem) {

  import as.dispatcher

  private[this] val http         = Http()
  private[this] val poolSettings = ConnectionPoolSettings("""akka.http.host-connection-pool {
                                          |max-connection-backoff = 0s
                                          |max-connections = 4
                                          |max-open-requests = 8
                                          |max-retries = 0
                                          |client.idle-timeout = 15s
                                          |client.parsing.illegal-header-warnings = off
                                          |}""".stripMargin)

  private[this] val userAgentHeaders = List(
    `User-Agent`("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.108 Safari/537.36")
  )

  private[this] val stdHeaders = userAgentHeaders ++ List(
    Accept(MediaRange(MediaTypes.`application/json`)),
    RawHeader("X-Requested-With", "XMLHttpRequest")
  )

  def upload(path: Path)(implicit session: YandexSession): Sink[ByteString, Future[Done]] = {
    Sink
      .lazyFutureSink { () ⇒
        val response = apiCall(
          "do-resource-upload-url",
          "dst"    → (Path.root / "disk" / path).toString,
          "force"  → "1",
          "size"   → "-1",
          "md5"    → "",
          "sha256" → ""
        )

        response
          .flatMap(model0Extract[String](_ \ "upload_url"))
          .map { uploadUrl ⇒
            val promise = Promise[Done]
            Flow[ByteString]
              .via(AkkaStreamUtils.extractUpstream)
              .mapAsync(1) { upstream ⇒
                http.singleRequest(
                  HttpRequest(HttpMethods.PUT, uploadUrl, userAgentHeaders, HttpEntity(ContentTypes.`application/octet-stream`, upstream)),
                  settings = poolSettings
                )
              }
              .map { response ⇒
                response.discardEntityBytes()
                if (!response.status.isSuccess()) throw new RuntimeException(s"Request failed: $response")
                Done
              }
              .alsoTo(AkkaStreamUtils.successPromiseOnFirst(promise))
              .to(Sink.ignore)
              .mapMaterializedValue(_ ⇒ promise.future)
          }
      }
      .mapMaterializedValue(_.flatten)
  }

  def download(path: Path)(implicit session: YandexSession): Source[ByteString, NotUsed] = {
    Source
      .lazyFuture { () ⇒
        apiCall("do-get-resource-url", "id" → (Path.root / "disk" / path).toString)
          .flatMap(model0Extract[String](_ \ "file"))
      }
      .mapAsync(1) { downloadUrl ⇒
        http.singleRequest(HttpRequest(HttpMethods.GET, "https:" + downloadUrl, userAgentHeaders), settings = poolSettings)
      }
      .mapAsync(1) { r ⇒
        if (r.status.isRedirection()) {
          val location = r.header[Location].get.uri
          http.singleRequest(HttpRequest(HttpMethods.GET, location, userAgentHeaders), settings = poolSettings)
        } else if (r.status.isSuccess()) {
          Future.successful(r)
        } else {
          Future.failed(new RuntimeException(s"Request failed: $r"))
        }
      }
      .flatMapConcat(_.entity.withoutSizeLimit().dataBytes)
  }

  def list(path: Path)(implicit session: YandexSession): Source[(String, Path), NotUsed] = {
    def listRec(offset: Long): Source[(String, Path), NotUsed] = {
      val limit = 1000 // Int.MaxValue

      Source
        .lazyFutureSource { () ⇒
          apiCall("resources", "idContext" → (Path.root / "disk" / path), "order" → 1, "sort" → "name", "offset" → offset, "amount" → limit)
            .flatMap(model0Extract[JsArray](_ \ "resources"))
            .map(_.value.map { e ⇒
              val entityType     = (e \ "type").as[String]
              val fullPath: Path = (e \ "path").as[String]
              val relativePath   = fullPath.toRelative(Path.root / "disk" / path)
              entityType → relativePath
            })
            .map { seq ⇒
              // println(s"${seq.length} entries at $offset")
              val source = Source(seq.toList)
              if (seq.length < limit) source
              else source.concat(listRec(offset + seq.length))
            }
        }
        .mapMaterializedValue(_ ⇒ NotUsed)
    }

    listRec(0)
  }

  def usedSpace()(implicit session: YandexSession): Future[YandexUsedSpace] = {
    apiCall("space", "locale" → "ru")
      .flatMap(model0Extract[YandexUsedSpace](identity))
  }

  def createFolder(path: Path)(implicit session: YandexSession): Future[Done] = {
    apiCall("do-resource-create-folder", "id" → (Path.root / "disk" / path), "force" → 1)
      .map { r ⇒
        r.discardEntityBytes()
        Done
      }
  }

  def delete(path: Path)(implicit session: YandexSession): Future[Done] = {
    apiCall("do-resource-delete", "id" → (Path.root / "disk" / path))
      .map { r ⇒
        r.discardEntityBytes()
        Done
      }
  }

  def createSession(login: String, password: String): Future[YandexSession] =
    for {
      (csrf, uuid, uidCookies) ← getCsrfToken()
      trackId                  ← authorize(csrf, uuid, login, uidCookies)
      (result, authCookies)    ← authorizePassword(csrf, trackId, password, uidCookies)
      sk                       ← requestDiskSK(authCookies)
    } yield YandexSession(result, authCookies, sk)

  private[this] def getCsrfToken() = {
    val passport  = "https://passport.yandex.ru/auth"
    val csrfR     = "\"csrf\":\"(?<csrf>.*?)\"".r
    val proccessR = "process_uuid=(?<uuid>[\\w-]+)".r

    http
      .singleRequest(HttpRequest(HttpMethods.GET, passport, stdHeaders), settings = poolSettings)
      .flatMap(entityWithCookies)
      .map { case (entity, cookies) ⇒
        val str     = entity.data.utf8String
        val csrf    = csrfR.findFirstMatchIn(str).map(_.group("csrf")).get
        val process = proccessR.findFirstMatchIn(str).map(_.group("uuid")).get
        (csrf, process, cookies)
      }
  }

  private[this] def entityWithCookies(resp: HttpResponse) = {
    val cookies = resp.headers.collect { case `Set-Cookie`(cookie) ⇒
      cookie
    }
    resp.entity.toStrict(5 seconds).map(_ → cookies)
  }

  private[this] def entityExtractV[T](f: JsValue ⇒ T)(resp: HttpResponse) = {
    resp.entity.toStrict(5 seconds).map(r ⇒ f(Json.parse(r.data.toArray)))
  }

  private[this] def entityExtract[T: Reads](f: JsValue ⇒ JsLookupResult)(resp: HttpResponse) = {
    entityExtractV(f(_).as[T])(resp)
  }

  private[this] def model0Extract[T: Reads](f: JsLookupResult ⇒ JsLookupResult)(resp: HttpResponse) = {
    entityExtract[T](r ⇒ f(r \ "models" \ 0 \ "data"))(resp)
  }

  private[this] def authorize(csrf: String, uuid: String, login: String, cookies: Seq[HttpCookie]) = {
    val uri = "https://passport.yandex.ru/registration-validations/auth/multi_step/start"
    val headers = List(
      Referer(
        Uri(
          "https://passport.yandex.ru/auth/list?from=cloud&origin=disk_landing_web_signin_ru&retpath=https%3A%2F%2Fdisk.yandex.ru%2Fclient%2Fdisk&backpath=https%3A%2F%2Fdisk.yandex.ru&mode=edit"
        )
      ),
      RawHeader("Sec-Fetch-Mode", "cors"),
      RawHeader("Sec-Fetch-Size", "same-origin"),
      Cookie(cookies.map(_.pair()).toList)
    )
    val form = FormData(
      "csrf_token"   → csrf,
      "process_uuid" → uuid,
      "login"        → login,
      "service"      → "cloud",
      "retpath"      → "https://disk.yandex.ru?source=landing2_signin_ru",
      "origin"       → "disk_landing2_signin_ru"
    )
    http
      .singleRequest(HttpRequest(HttpMethods.POST, uri, headers ++ userAgentHeaders, form.toEntity), settings = poolSettings)
      .flatMap(_.entity.toStrict(5 seconds))
      .map { response ⇒
        (Json.parse(response.data.toArray) \ "track_id").as[String]
      }
  }

  private[this] def authorizePassword(csrf: String, trackId: String, password: String, cookies: Seq[HttpCookie]) = {
    val headers = stdHeaders ++ List(
      RawHeader("Sec-Fetch-Dest", "empty"),
      RawHeader("Sec-Fetch-Mode", "cors"),
      RawHeader("Sec-Fetch-Site", "same-origin"),
      Cookie(cookies.map(_.pair()).toList)
    )

    def getCaptcha() = {
      val url = "https://passport.yandex.ru/registration-validations/textcaptcha"
      val form = FormData(
        "csrf_token"   → csrf,
        "track_id"     → trackId,
        "scale_factor" → "3"
      )
      http
        .singleRequest(HttpRequest(HttpMethods.POST, url, headers, form.toEntity), settings = poolSettings)
        .flatMap(entityExtractV { json ⇒
          val image = (json \ "image_url").as[String]
          val key   = (json \ "key").as[String]
          (image, key)
        })
    }

    def checkCaptcha(key: String, answer: String) = {
      val url = "https://passport.yandex.ru/registration-validations/checkHuman"
      val form = FormData(
        "csrf_token" → csrf,
        "track_id"   → trackId,
        "answer"     → answer,
        "key"        → key
      )

      http
        .singleRequest(HttpRequest(HttpMethods.POST, url, headers, form.toEntity), settings = poolSettings)
        .flatMap(entityExtractV { response ⇒
          val valid = (response \ "status").as[String] == "ok"
          require(valid, s"Captcha verification failed: $response")
          Done
        })
    }

    val uri = "https://passport.yandex.ru/registration-validations/auth/multi_step/commit_password"
    val form = FormData(
      "csrf_token" → csrf,
      "track_id"   → trackId,
      "password"   → password
    )
    val passwordRequest = HttpRequest(HttpMethods.POST, uri, headers, form.toEntity)

    def executePasswordRequest() =
      http
        .singleRequest(passwordRequest, settings = poolSettings)
        .flatMap(entityWithCookies)

    executePasswordRequest()
      .flatMap { case (response, cookies) ⇒
        val json = Json.parse(response.data.toArray)
        if ((json \ "errors" \ 0).asOpt[String].contains("captcha.required")) {
          for {
            (image, key) ← getCaptcha()
            answer       ← solveCaptcha(image)
            Done         ← checkCaptcha(key, answer)
            result       ← executePasswordRequest()
          } yield result
        } else if ((json \ "redirect_url").asOpt[String].isDefined) {
          val url = s"https://passport.yandex.ru${(json \ "redirect_url").as[String]}"
          passChallenge(url).map(_ ⇒ (response → cookies))
        } else {
          Future.successful(response → cookies)
        }
      }
      .flatMap { case (response, sessCookies) ⇒
        val json = Json.parse(response.data.toArray)
        require((json \ "status").as[String] == "ok", s"Not ok: $json")
        val validateUrl = "https://passport.yandex.ru/registration-validations/auth/accounts"
        val headers = List(
          Cookie((cookies ++ sessCookies).map(_.pair()).toList)
        )
        http
          .singleRequest(HttpRequest(HttpMethods.POST, validateUrl, headers, FormData("csrf_token" → csrf).toEntity), settings = poolSettings)
          .flatMap(entityWithCookies)
          .map { case (entity, authCookies) ⇒
            Json.parse(entity.data.toArray).as[JsObject] → (cookies ++ sessCookies ++ authCookies)
          }
      }
  }

  private[this] def requestDiskSK(cookies: Seq[HttpCookie]) = {
    val headers = List(
      RawHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3"),
      RawHeader("Sec-Fetch-Mode", "navigate"),
      RawHeader("Sec-Fetch-Site", "same-site"),
      RawHeader("Sec-Fetch-User", "?1"),
      Referer(
        "https://passport.yandex.ru/auth/list?from=cloud&origin=disk_landing_web_signin_ru&retpath=https%3A%2F%2Fdisk.yandex.ru%2Fclient%2Fdisk&backpath=https%3A%2F%2Fdisk.yandex.ru&mode=edit"
      ),
      Cookie(cookies.map(_.pair()).toList)
    )

    val regex = "\"sk\":\"(?<sk>.+?)\"".r
    http
      .singleRequest(HttpRequest(HttpMethods.GET, "https://disk.yandex.ru/client/disk", headers ++ userAgentHeaders), settings = poolSettings)
      .flatMap(entityWithCookies)
      .map { case (entity, _) ⇒
        regex
          .findFirstMatchIn(entity.data.utf8String)
          .map(_.group("sk"))
          .getOrElse(throw new IllegalArgumentException("No sk found"))
      }
  }

  private[this] def apiCall[T](model: String, params: (String, Any)*)(implicit session: YandexSession) = {
    val url = s"https://disk.yandex.ru/models/?_m=$model"
    val form = FormData(
      Seq("sk" → session.sessionKey, "idClient" → "", "_model.0" → model) ++
        params.map { case (k, v) ⇒ s"$k.0" → String.valueOf(v) }: _*
    )
    val request = HttpRequest(
      HttpMethods.POST,
      url,
      List(
        Referer("https://disk.yandex.ru/client/disk")
      ) ++ session.headers ++ stdHeaders,
      form.toEntity
    )

    http.singleRequest(request, settings = poolSettings).map { r ⇒
      if (r.status.isSuccess()) r
      else throw new IllegalArgumentException(s"Request failed: $request -> $r")
    }
  }
}
