package com.karasiq.shadowcloud.storage.gdrive

import java.io.InputStream

import akka.NotUsed
import akka.stream.ActorAttributes.Dispatcher
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import akka.stream.{ActorAttributes, Supervision}
import com.karasiq.common.memory.SizeUnit
import com.karasiq.gdrive.files.GDriveService.TeamDriveId
import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Try

private[gdrive] object GDriveRepository {
  def apply(service: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId): GDriveRepository = {
    new GDriveRepository(service)
  }
}

private[gdrive] class GDriveRepository(service: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId) extends PathTreeRepository {
  private[this] val entityCache = GDriveEntityCache(service)

  def subKeys(fromPath: Path) = {
    type EntityWithPath = (service.EntityPath, GDrive.Entity)

    val traverseFlow: Flow[Path, EntityWithPath, NotUsed] = {
      def iteratorTry(path: Path) =
        Try {
          service.traverseFolder(path.nodes)
        } recover {
          case _ ⇒
            Await.result(entityCache.getOrCreateFolderId(path), 3 minutes)
            service.traverseFolder(path.nodes)
        }

      Flow[Path]
        .flatMapConcat(path ⇒ Source.fromIterator(() ⇒ iteratorTry(path).get))
        .idleTimeout(30 seconds)
        .withAttributes(defaultAttributes)
        .named("gdriveTraverse")
    }

    Source
      .single(fromPath)
      .via(traverseFlow)
      .alsoToMat(
        Flow[EntityWithPath]
          .fold(0L)((c, _) ⇒ c + 1)
          .via(StorageUtils.wrapCountStream(fromPath))
          .toMat(Sink.head)(Keep.right)
      )(Keep.right)
      // .log("gdrive-entities")
      .map {
        case (pathNodes, file) ⇒
          val parent = Path(pathNodes).toRelative(fromPath)
          parent / file.name
      }
      .named("gdriveKeys")
  }

  def read(key: Path) = {
    val idsSource = Source
      .single(key)
      .mapAsync(1)(entityCache.getFileIds(_))
      // .initialTimeout(15 seconds)
      // .log("gdrive-file-ids")
      .withAttributes(defaultAttributes)

    val blockingSource = idsSource
      .viaMat(AkkaStreamUtils.flatMapConcatMat { fileIds ⇒
        if (fileIds.isEmpty) throw StorageException.NotFound(key)
        StreamConverters
          .fromInputStream(() ⇒ service.download(fileIds.head), SizeUnit.MB.intValue)
          .mapMaterializedValue(StorageUtils.wrapAkkaIOFuture(key, _))
      })(Keep.right)
      .mapMaterializedValue(fs ⇒ StorageUtils.wrapFuture(key, fs.map(StorageUtils.foldIOResults)))

    blockingSource
      .withAttributes(fileStreamAttributes)
      .named("gdriveRead")
  }

  def write(key: Path) = {
    def getFolderId(path: Path) =
      for {
        folderId   ← entityCache.getOrCreateFolderId(path.parent)
        fileExists ← entityCache.isFileExists(path)
      } yield {
        if (fileExists) throw StorageException.AlreadyExists(path)
        folderId
      }

    def blockingUpload(folderId: String, name: String, inputStream: InputStream): Long = {
      val entity = service.upload(folderId, name, inputStream)
      entity.size
    }

    Flow[Data]
      .via(AkkaStreamUtils.extractUpstream)
      .zip(Source.lazyFuture(() => getFolderId(key)))
      .flatMapConcat {
        case (stream, folderId) =>
          stream.via(
            AkkaStreamUtils.writeInputStream(
              { inputStream =>
                val result = Try(blockingUpload(folderId, key.name, inputStream))
                result.foreach(_ => entityCache.resetFileCache(key))
                Source.future(Future.fromTry(result))
              },
              Dispatcher(GDriveDispatchers.fileDispatcherId)
            )
          )
      }
      .map(written => StorageIOResult.Success(key, written))
      .withAttributes(fileStreamAttributes)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .toMat(Sink.head)(Keep.right)
      .named("gdriveWrite")
  }

  def delete = {
    Flow[Path]
      .mapAsyncUnordered(2) { path ⇒
        def isDeleted(fileId: String) = Try(service.delete(fileId)).isSuccess
        val deletedCount              = entityCache.getFileIds(path, cached = false).map(_.count(isDeleted))
        entityCache.resetFileCache(path)
        StorageUtils.wrapCountFuture(path, deletedCount)
      }
      .idleTimeout(15 seconds)
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .withAttributes(defaultAttributes)
  }

  private[this] def defaultAttributes = {
    ActorAttributes.dispatcher(GDriveDispatchers.apiDispatcherId)
  }

  private[this] def fileStreamAttributes = {
    ActorAttributes.dispatcher(GDriveDispatchers.fileDispatcherId)
  }

}
