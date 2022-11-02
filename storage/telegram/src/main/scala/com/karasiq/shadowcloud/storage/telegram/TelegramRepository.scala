package com.karasiq.shadowcloud.storage.telegram

import java.io.IOException
import java.net.{URLDecoder, URLEncoder}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.scaladsl.{Flow, Framing, Keep, Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.{AkkaStreamUtils, ByteStreams}

import scala.concurrent.Future
import scala.concurrent.duration._

object TelegramRepository {
  def apply(port: Int)(implicit as: ActorSystem): TelegramRepository =
    new TelegramRepository(port)
}

class TelegramRepository(port: Int)(implicit as: ActorSystem) extends PathTreeRepository {
  import as.dispatcher

  private[this] val host = "localhost"
  private[this] val log  = Logging(as, getClass)

  private[this] val executeHttpRequest = {
    val settings = ConnectionPoolSettings("""akka.http.host-connection-pool {
        |max-connection-backoff = 5s
        |max-connections = 12
        |max-open-requests = 16
        |max-retries = 0
        |client.idle-timeout = 600s
        |}""".stripMargin)

    Flow[HttpRequest]
      .map(_ → NotUsed)
      .via(Http().cachedHostConnectionPool(host, port, settings, log))
      .collect { case (response, _) ⇒ response.get }
  }

  override def subKeys(fromPath: Path): Source[Path, Result] =
    Source
      .single(
        HttpRequest(
          HttpMethods.GET,
          Uri("/list").withQuery(Uri.Query("path" → encodePath(fromPath)))
        )
      )
      .via(executeHttpRequest)
      .via(requireHttpSuccess)
      .flatMapConcat(_.entity.dataBytes)
      .alsoToMat(StorageUtils.countPassedBytes(fromPath).toMat(Sink.head)(Keep.right))(Keep.right)
      .via(Framing.delimiter(ByteString("\n"), 8192))
      .filter(_.nonEmpty)
      .map(bs ⇒ decodePath(bs.utf8String).toRelative(fromPath))

  override def read(key: Path): Source[Data, Result] =
    Source
      .single(
        HttpRequest(
          HttpMethods.GET,
          Uri("/download").withQuery(Uri.Query("path" → encodePath(key)))
        )
      )
      .via(executeHttpRequest)
      .via(requireHttpSuccess)
      .flatMapConcat(_.entity.withoutSizeLimit().dataBytes)
      .alsoToMat(StorageUtils.countPassedBytes(key).toMat(Sink.head)(Keep.right))(Keep.right)

  override def write(path: Path): Sink[Data, Result] = {
    Flow[Data]
      .viaMat(
        AkkaStreamUtils.alsoToWaitForAll(
          Flow[Data]
            .via(StorageUtils.countPassedBytes(path))
            .toMat(Sink.head)(Keep.right)
        )
      )(Keep.right)
      // .via(ByteStreams.concat)
      .via(AkkaStreamUtils.extractUpstream)
      .map(upstream ⇒
        HttpRequest(
          HttpMethods.POST,
          Uri("/upload").withQuery(Uri.Query("path" → encodePath(path))),
          entity = HttpEntity(ContentTypes.`application/octet-stream`, upstream)
        )
      )
      .via(executeHttpRequest)
      .alsoTo(Sink.foreach(_.discardEntityBytes()))
      .map {
        case r if r.status.isSuccess()             ⇒ StorageIOResult.Success(path, 0)
        case r if r.status == StatusCodes.Conflict ⇒ StorageIOResult.Failure(path, StorageException.AlreadyExists(path))
        case r                                     ⇒ StorageIOResult.Failure(path, StorageUtils.wrapException(path, new IOException(s"Request error: $r")))
      }
      .via(StorageUtils.wrapStream(path))
      .toMat(Sink.headOption) { case (bytesCount, reqResult) ⇒
        val result = reqResult.map(_.getOrElse(StorageUtils.failure(path, new IOException)))
        StorageUtils.foldIOFutures(result, bytesCount)
      }
    //.toMat(Sink.headOption)(Keep.right(_, _).map(_.getOrElse(StorageUtils.failure(path, new IOException))))
  }

  override def delete: Sink[Path, Result] =
    Flow[Path]
      .map(path ⇒ HttpRequest(HttpMethods.DELETE, Uri("/delete").withQuery(Uri.Query("path" → encodePath(path)))))
      .via(executeHttpRequest)
      .via(StorageUtils.countPassedElements())
      .toMat(Sink.head)(Keep.right)

  def fileSizes(path: Path): Future[Long] = {
    Source
      .single(HttpRequest(uri = Uri("/size").withQuery(Uri.Query("path" → encodePath(path)))))
      .via(executeHttpRequest)
      .via(requireHttpSuccess)
      .mapAsync(1)(_.entity.toStrict(30 minutes, 20))
      .map(_.data.utf8String.toLong)
      .runWith(Sink.head)
  }

  private[this] def decodePath(path: String) = Path(path.split('/').map(URLDecoder.decode(_, "UTF-8")))
  private[this] def encodePath(path: Path)   = path.nodes.map(URLEncoder.encode(_, "UTF-8")).mkString("/")

  private[this] def requireHttpSuccess: Flow[HttpResponse, HttpResponse, NotUsed] = Flow[HttpResponse].map { response ⇒
    if (response.status.isSuccess())
      response
    else {
      response.discardEntityBytes()
      throw new IOException(s"Failed: $response")
    }
  }
}
