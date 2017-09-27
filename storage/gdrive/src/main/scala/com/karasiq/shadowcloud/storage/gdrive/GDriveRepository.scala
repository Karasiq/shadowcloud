package com.karasiq.shadowcloud.storage.gdrive

import java.io.InputStream

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

import akka.NotUsed
import akka.stream.ActorAttributes
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import com.google.common.io.CountingInputStream

import com.karasiq.common.memory.SizeUnit
import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

private[gdrive] object GDriveRepository {
  def apply(service: GDriveService)(implicit ec: ExecutionContext): GDriveRepository = {
    new GDriveRepository(service)
  }
}

private[gdrive] class GDriveRepository(service: GDriveService)(implicit ec: ExecutionContext) extends PathTreeRepository {
  private[this] val entityCache = GDriveEntityCache(service)

  def keys = {
    subKeys(Path.root)
  }

  override def subKeys(fromPath: Path) = {
    type EntityWithPath = (service.EntityPath, GDrive.Entity)

    val traverseFlow: Flow[Path, EntityWithPath, NotUsed] = {
      def iteratorTry(path: Path) = Try {
        service.traverseFolder(path.nodes)
      } recover { case _ ⇒
        entityCache.getOrCreateFolderId(path)
        service.traverseFolder(path.nodes)
      }

      Flow[Path]
        .flatMapConcat(path ⇒ Source.fromIterator(() ⇒ iteratorTry(path).get))
        .withAttributes(defaultAttributes)
        .named("gdriveTraverse")
    }

    Source.single(fromPath)
      .via(traverseFlow)
      .alsoToMat(
        Flow[EntityWithPath]
          .fold(0L)((c, _) ⇒ c + 1)
          .via(StorageUtils.wrapCountStream(fromPath))
          .toMat(Sink.head)(Keep.right)
      )(Keep.right)
      // .log("gdrive-entities")
      .map { case (pathNodes, file) ⇒
        val parent = Path(pathNodes).toRelative(fromPath)
        parent / file.name
      }
      .named("gdriveKeys")
  }

  def read(key: Path) = {
    val idsSource = Source.single(key)
      .map(entityCache.getFileIds(_))
      .log("gdrive-file-ids")
      .withAttributes(defaultAttributes)

    val blockingSource = idsSource
      .viaMat(AkkaStreamUtils.flatMapConcatMat { fileIds ⇒
        if (fileIds.isEmpty) throw StorageException.NotFound(key)
        StreamConverters
          .fromInputStream(() ⇒ service.download(fileIds.head), SizeUnit.MB.intValue)
          .mapMaterializedValue(StorageUtils.wrapIOResult(key, _))
      })(Keep.right)
      .mapMaterializedValue(fs ⇒ StorageUtils.wrapFuture(key, fs.map(StorageUtils.foldIOResults)))

    blockingSource
      .withAttributes(fileStreamAttributes)
      .named("gdriveRead")
  }

  def write(key: Path) = {
    def blockingUpload(path: Path, inputStream: InputStream) = {
      val folderId = entityCache.getOrCreateFolderId(path.parent)
      if (entityCache.isFileExists(path)) throw StorageException.AlreadyExists(path)

      val countingInputStream = new CountingInputStream(inputStream)
      service.upload(folderId, path.name, countingInputStream)
      countingInputStream.getCount
    }

    val blockingSink = Flow[Data]
      .via(AkkaStreamUtils.extractUpstream)
      .viaMat(AkkaStreamUtils.flatMapConcatMat { dataStream ⇒
        val promise = Promise[InputStream]
        val writeStream = Source.fromFuture(promise.future)
          .initialTimeout(5 seconds)
          .map { inputStream ⇒
            val result = Try(blockingUpload(key, inputStream))
            result.failed.foreach(_ ⇒ inputStream.close())
            result.get
          }
          .via(StorageUtils.wrapCountStream(key))
          .alsoToMat(Sink.head)(Keep.right)
          .withAttributes(fileStreamAttributes)
        
        dataStream
          .alsoToMat(StreamConverters.asInputStream(15 seconds))(Keep.right)
          .mapMaterializedValue(promise.success)
          .alsoTo(AkkaStreamUtils.failPromiseOnFailure(promise))
          .viaMat(AkkaStreamUtils.dropUpstream(writeStream))(Keep.right)
      })(Keep.right)
      .to(Sink.ignore)
      .mapMaterializedValue(fs ⇒ StorageUtils.wrapFuture(key, fs.map(StorageUtils.foldIOResults)))

    blockingSink
      .named("gdriveWrite")
  }

  def delete = {
    Flow[Path]
      .log("gdrive-delete")
      .mapAsyncUnordered(2) { path ⇒
        def isDeleted(fileId: String) = Try(service.delete(fileId)).isSuccess
        val deletedCount = Try(entityCache.getFileIds(path).count(isDeleted))
        StorageUtils.wrapCountFuture(path, Future.fromTry(deletedCount))
      }
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
