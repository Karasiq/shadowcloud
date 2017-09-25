package com.karasiq.shadowcloud.storage.gdrive

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

import akka.NotUsed
import akka.actor.ActorContext
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import com.google.common.io.CountingInputStream

import com.karasiq.gdrive.files.GDriveService
import com.karasiq.shadowcloud.exceptions.{SCException, StorageException}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils

private[gdrive] object GDriveRepository {
  def create(service: GDriveService)(execCtx: ExecutionContext, fileExecCtx: ExecutionContext): GDriveRepository = {
    new GDriveRepository(service)(execCtx, fileExecCtx)
  }

  def apply(service: GDriveService)(implicit ac: ActorContext): GDriveRepository = {
    val dispatchers = ac.system.dispatchers
    create(service)(dispatchers.lookup(GDriveDispatchers.apiDispatcherId), dispatchers.lookup(GDriveDispatchers.fileDispatcherId))
  }
}

private[gdrive] class GDriveRepository(service: GDriveService)(execCtx: ExecutionContext, fileExecCtx: ExecutionContext) extends PathTreeRepository {
  private[this] implicit val implicitExecCtx = execCtx
  private[this] val foldersCache = TrieMap.empty[Path, String]

  def keys = {
    subKeys(Path.root)
  }

  override def subKeys(fromPath: Path) = {
    type EntityMap = Map[service.EntityPath, service.EntityList]

    def traverseFolder(): Future[EntityMap] = {
      Future(service.traverseFolder(fromPath.nodes))
        .recover { case error if SCException.isNotFound(error) ⇒ Map.empty }
    }
    
    Source.single(NotUsed)
      .map(_ ⇒ traverseFolder())
      .alsoToMat(
        Flow[Future[EntityMap]]
          .mapAsync(1)(f ⇒ StorageUtils.wrapCountFuture(fromPath, f.map(_.size)))
          .toMat(Sink.head)(Keep.right)
      )(Keep.right)
      .mapAsync(1)(identity)
      .mapConcat(_.map { case (pathNodes, entities) ⇒
        val parent = Path(pathNodes.toVector)
        entities.map(e ⇒ parent / e.name)
      })
      .mapConcat(_.toVector)
      // .log("gdrive-keys")
      .map(_.toRelative(fromPath))
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .named("gdriveKeys")
  }

  def read(key: Path) = {
    /* val concatSource = Source.single(key)
      .mapAsync(1)(key ⇒ getFileId(key).map { fileId ⇒
        val outputStream = ByteStringOutputStream()
        service.download(fileId, outputStream)
        outputStream.toByteString
      })
      .alsoToMat(Sink.head)(Keep.right)
      .mapMaterializedValue(future ⇒ StorageUtils.wrapFuture(key, future.map(bytes ⇒ StorageIOResult.Success(key, bytes.length)))) */

    /* val blockingSource1 = StreamConverters.asOutputStream(15 seconds).mapMaterializedValue { outputStream ⇒
      val countingOutputStream = new CountingOutputStream(outputStream)
      val future = getFileId(key)
        .flatMap(fileId ⇒ Future { service.download(fileId, countingOutputStream); countingOutputStream.getCount }(fileExecCtx))

      future.onComplete(_ ⇒ countingOutputStream.close())(fileExecCtx)
      StorageUtils.wrapCountFuture(key, future)
    } */

    val blockingSource = {
      val streamFuture = getFileIds(key).map { fileIds ⇒
        StreamConverters.fromInputStream(() ⇒ service.download(fileIds.head))
          .mapMaterializedValue(StorageUtils.wrapIOResult(key, _))
      }

      Source.fromFutureSource(streamFuture).mapMaterializedValue(_.flatten)
    }

    blockingSource.named("gdriveRead")
  }

  def write(key: Path) = {
    /* val concatSink = Flow[ByteString]
      .via(ByteStreams.concat)
      .flatMapConcat { bytes ⇒
        val future = getFolderId(key.parent).zip(isFileExists(key)).map { case (folderId, isFileExists) ⇒
          if (isFileExists) throw StorageException.AlreadyExists(key)
          service.upload(folderId, key.name, ByteStringInputStream(bytes))
        }
        Source.fromFuture(future).map(_ ⇒ bytes)
      }
      .map(bytes ⇒ StorageIOResult.Success(key, bytes.length))
      .toMat(Sink.head)(Keep.right)
      .mapMaterializedValue(future ⇒ StorageUtils.wrapFuture(key, future.map(_ ⇒ StorageIOResult.Success(key, 0)))) */
    
    val blockingSink = StreamConverters.asInputStream(15 seconds).mapMaterializedValue { inputStream ⇒
      val countingInputStream = new CountingInputStream(inputStream)
      val future = getOrCreateFolderId(key.parent).zip(isFileExists(key)).flatMap { case (folderId, isFileExists) ⇒
        if (isFileExists) throw StorageException.AlreadyExists(key)
        Future { service.upload(folderId, key.name, inputStream); countingInputStream.getCount }(fileExecCtx)
      }
      future.failed.foreach(_ ⇒ countingInputStream.close())(fileExecCtx)
      StorageUtils.wrapCountFuture(key, future)
    }

    blockingSink.named("gdriveWrite")
  }

  def delete = {
    Flow[Path]
      .log("gdrive-delete")
      .mapAsyncUnordered(4) { path ⇒
        def isDeleted(fileId: String) = {
          val result = Try(service.delete(fileId))
          result.isSuccess
        }
        val deletedFuture = getFileIds(path).map(_.count(isDeleted))
        StorageUtils.wrapCountFuture(path, deletedFuture)
      }
      .fold(Seq.empty[StorageIOResult])(_ :+ _)
      .map(StorageUtils.foldIOResultsIgnoreErrors)
      .recover { case error ⇒ StorageIOResult.Failure(Path.root, StorageUtils.wrapException(Path.root, error)) }
      .orElse(Source.single(StorageIOResult.Success(Path.root, 0)))
      .toMat(Sink.head)(Keep.right)
  }

  private[this] def getOrCreateFolderId(path: Path) = {
    Future(foldersCache.getOrElseUpdate(path, service.createFolder(path.nodes).id))
  }

  private[this] def getFolderId(path: Path) = {
    Future(foldersCache.getOrElseUpdate(path, service.folder(path.nodes).id))
  }

  private[this] def getFileIds(path: Path) = {
    getFolderId(path.parent).map { folderId ⇒
      service.files(folderId, path.name).map(_.id)
    }
  }

  private[this] def isFileExists(path: Path) = {
    getFileIds(path)
      .map(_.nonEmpty)
      .recover { case error if SCException.isNotFound(error) ⇒ false }
  }
}
