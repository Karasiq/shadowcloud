// modules/storage-telegram/src/main/scala/com/karasiq/shadowcloud/storage/TelegramStorage.scala

package com.karasiq.shadowcloud.storage

import akka.actor.ActorSystem
import akka.http.scaladsl.client.HttpClientPool
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.Materializer
import com.karasiq.shadowcloud.storage.dispatchers.StorageDispatcher
import play.api.libs.json._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import java.io._

class TelegramHttpClient(
                          baseUrl: String = "http://127.0.0.1:8000"
                        )(implicit as: ActorSystem, mat: Materializer, ec: ExecutionContext) {

  private val http = Http()

  def health(): Future[JsValue] = {
    makeRequest(HttpMethods.GET, "/health")
  }

  def uploadFile(chatId: Long, filePath: String, caption: String = ""): Future[Long] = {
    val file = new File(filePath)
    val fileBody = HttpEntity(ContentTypes.`application/octet-stream`, file)

    val uri = s"$baseUrl/upload?chat_id=$chatId&caption=${java.net.URLEncoder.encode(caption, "UTF-8")}"
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = uri,
      entity = fileBody
    )

    http.singleRequest(request).flatMap { response =>
      response.entity.dataBytes
        .runFold(scala.collection.mutable.ArrayBuffer[Byte]())(_ += _)
        .map { bytes =>
          val json = Json.parse(bytes.toArray)
          (json \ "message_id").as[Long]
        }
    }
  }

  def downloadFile(chatId: Long, messageId: Long, outputPath: String): Future[String] = {
    val uri = s"$baseUrl/download?chat_id=$chatId&message_id=$messageId"
    val request = HttpRequest(method = HttpMethods.GET, uri = uri)

    http.singleRequest(request).flatMap { response =>
      val file = new File(outputPath)
      file.getParentFile.mkdirs()

      response.entity.dataBytes
        .runWith(akka.stream.scaladsl.FileIO.toFile(file))
        .map { _ =>
          outputPath
        }
    }
  }

  def listMessages(chatId: Long, limit: Int = 100): Future[JsValue] = {
    val uri = s"$baseUrl/messages?chat_id=$chatId&limit=$limit"
    makeRequest(HttpMethods.GET, uri)
  }

  def getInfo(): Future[JsValue] = {
    makeRequest(HttpMethods.GET, "/info")
  }

  private def makeRequest(method: HttpMethod, path: String): Future[JsValue] = {
    val uri = if (path.startsWith("http")) path else s"$baseUrl$path"
    val request = HttpRequest(method = method, uri = uri)

    http.singleRequest(request).flatMap { response =>
      response.entity.dataBytes
        .runFold(scala.collection.mutable.ArrayBuffer[Byte]())(_ += _)
        .map { bytes =>
          Json.parse(bytes.toArray)
        }
    }
  }
}

class TelegramStorageDispatcher(config: StorageConfig)(implicit as: ActorSystem, mat: Materializer)
  extends StorageDispatcher {

  implicit val ec = as.dispatcher

  private val daemonUrl = config.path.getOrElse("http://127.0.0.1:8000")
  private val chatId = config.credentials
    .flatMap(_.props.get("chat_id"))
    .map(_.as[Long])
    .getOrElse(throw new RuntimeException("chat_id not configured"))

  private lazy val client = new TelegramHttpClient(daemonUrl)

  override def init(): Future[Unit] = {
    client.health().map { response =>
      if ((response \ "status").asOpt[String].contains("healthy")) {
        ()
      } else {
        throw new RuntimeException("Telegram daemon not healthy")
      }
    }
  }

  override def shutdown(): Future[Unit] = Future.successful(())

  override def upload(path: String, data: InputStream): Future[StorageId] = {
    // Сохраняем временный файл
    val tempFile = File.createTempFile("telegram_upload", ".dat")
    tempFile.deleteOnExit()

    val fos = new FileOutputStream(tempFile)
    try {
      val buffer = new Array[Byte](1024 * 64)
      var bytesRead = data.read(buffer)
      while (bytesRead > 0) {
        fos.write(buffer, 0, bytesRead)
        bytesRead = data.read(buffer)
      }
    } finally {
      fos.close()
      data.close()
    }

    // Загружаем на сервер
    client.uploadFile(chatId, tempFile.getAbsolutePath, path)
      .map { messageId =>
        tempFile.delete()
        StorageId(messageId.toString)
      }
  }

  override def download(storageId: StorageId): Future[InputStream] = {
    val messageId = storageId.toString.toLong
    val tempFile = File.createTempFile("telegram_download", ".dat")
    tempFile.deleteOnExit()

    client.downloadFile(chatId, messageId, tempFile.getAbsolutePath)
      .map { _ =>
        new FileInputStream(tempFile)
      }
  }
}
