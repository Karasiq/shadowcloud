package com.karasiq.shadowcloud.storage.telegram

import java.io.InputStream

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.ActorAttributes.Dispatcher
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import akka.stream.{ActorAttributes, Supervision}
import akka.util.ByteString

import com.karasiq.common.memory.SizeUnit
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * Telegram storage repository wrapper using TelegramDaemon client.
  *
  * Features:
  * - Streaming file upload/download with chunking
  * - Automatic session persistence
  * - Type-safe Telegram API integration
  * - Akka Streams-based reactive operations
  */
private[telegram] class TelegramRepository(
    daemon: TelegramDaemon,
    sc: ShadowCloudExtension,
    log: LoggingAdapter
)(implicit ec: ExecutionContext)
    extends PathTreeRepository {

  /**
    * List all files/folders at a given path.
    *
    * @param fromPath starting path
    * @return source of subpaths
    */
  def subKeys(fromPath: Path) = {
    Source
      .unfold(0) {
        case offset if offset >= 100000 ⇒
          None

        case offset ⇒
          val newOffset = offset + 1000
          Some(newOffset → offset)
      }
      .mapAsync(1)(offset ⇒ daemon.list(limit = 1000, offset = offset))
      .takeWhile(_.nonEmpty)
      .mapConcat(identity)
      .map((file: TelegramFile) ⇒ StorageIOResult.Success(fromPath / file.fileName, file.fileSize))
      .via(StorageUtils.wrapStream(fromPath))
      .withAttributes(defaultAttributes)
      .named("telegramSubKeys")
  }

  /**
    * Read file content as a stream.
    *
    * @param key file path (uses filename for message ID lookup)
    * @return source of file data bytes
    */
  def read(key: Path) = {
    Source
      .lazyFuture(() ⇒ parseMessageId(key))
      .mapAsync(1) { messageId ⇒
        daemon.downloadStream(messageId)
      }
      .flatMapConcat(identity)
      .alsoToMat(StorageUtils.countPassedBytes(key).toMat(Sink.head)(Keep.right))(Keep.right)
      .withAttributes(fileStreamAttributes)
      .named("telegramRead")
  }

  /**
    * Write file content from a stream to Telegram.
    *
    * @param path target file path (becomes filename on Telegram)
    * @return sink that accepts data bytes
    */
  def write(path: Path) = {
    Flow[ByteString]
      .via(AkkaStreamUtils.extractUpstream)
      .mapAsyncUnordered(1) { stream ⇒
        Future.successful((stream, path.name))
      }
      .flatMapConcat {
        case (stream, fileName) ⇒
          stream.via(
            AkkaStreamUtils.writeInputStream(
              { inputStream ⇒
                val result = Try {
                  daemon.uploadStream(
                    StreamConverters.fromInputStream(() ⇒ inputStream),
                    fileName,
                    caption = path.toString
                  )
                }

                Source.future(Future.fromTry(result).flatten)
              },
              Dispatcher("telegram-file-dispatcher")
            )
          )
      }
      .map(messageId ⇒ StorageIOResult.Success(path, 1L))
      .withAttributes(fileStreamAttributes)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .via(StorageUtils.wrapStream(path))
      .toMat(Sink.head)(Keep.right)
      .named("telegramWrite")
  }

  /**
    * Delete files at given paths.
    *
    * @return sink that accepts paths to delete
    */
  def delete = {
    Flow[Path]
      .mapAsyncUnordered(2) { path ⇒
        for {
          messageId ← parseMessageId(path)
          _         ← daemon.deleteFile(messageId)
        } yield path

        StorageUtils.wrapCountFuture(path, Future.successful(1))
      }
      .idleTimeout(15.seconds)
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .withAttributes(defaultAttributes)
  }

  /**
    * Parse message ID from path.
    * In production, would use metadata storage for mapping.
    * For now, assumes path is numeric message ID.
    */
  private def parseMessageId(path: Path): Future[Long] = {
    Future {
      path.name.toLong
    }.recoverWith {
      case _ ⇒
        Future.failed(StorageException.NotFound(path))
    }
  }

  private def defaultAttributes = {
    ActorAttributes.dispatcher("telegram-api-dispatcher")
  }

  private def fileStreamAttributes = {
    ActorAttributes.dispatcher("telegram-file-dispatcher")
  }
}

object TelegramRepository {
  def apply(
      daemon: TelegramDaemon,
      sc: ShadowCloudExtension,
      log: LoggingAdapter
  )(implicit ec: ExecutionContext): TelegramRepository = {
    new TelegramRepository(daemon, sc, log)
  }
}
