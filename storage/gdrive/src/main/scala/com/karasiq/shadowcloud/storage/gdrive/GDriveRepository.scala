package com.karasiq.shadowcloud.storage.gdrive

import java.io.FileNotFoundException

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import com.google.common.io.CountingOutputStream

import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils

private[gdrive] object GDriveRepository {
  def apply(service: GDriveService)(implicit ec: ExecutionContext): GDriveRepository = {
    new GDriveRepository(service)
  }
}

private[gdrive] class GDriveRepository(service: GDriveService)(implicit ec: ExecutionContext) extends PathTreeRepository {
  private[this] val foldersCache = TrieMap.empty[Path, String]
  private[this] val filesCache = TrieMap.empty[Path, String]

  def keys = {
    subKeys(Path.root)
  }

  override def subKeys(fromPath: Path) = {
    type FileMapT = Map[Seq[String], Seq[GDrive.Entity]]

    def traverseFolder(): Future[FileMapT] = {
      Future(service.traverseFolder(fromPath.nodes))
        .recover { case _: NoSuchElementException | _: FileNotFoundException ⇒ Map.empty }
    }
    
    Source.single(NotUsed)
      .map(_ ⇒ traverseFolder())
      .alsoToMat(
        Flow[Future[FileMapT]]
          .mapAsync(1)(future ⇒ StorageUtils.wrapFuture(fromPath, future.map(map ⇒ StorageIOResult.Success(fromPath, map.size))))
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

    val blockingSource = StreamConverters.asOutputStream(15 seconds).mapMaterializedValue { outputStream ⇒
      val countingOutputStream = new CountingOutputStream(outputStream)
      val future = getFileId(key)
        .map { fileId ⇒ service.download(fileId, countingOutputStream); countingOutputStream.getCount }

      future.onComplete(_ ⇒ countingOutputStream.close())
      StorageUtils.wrapFuture(key, future.map(StorageIOResult.Success(key, _)))
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
      val future = getOrCreateFolderId(key.parent).zip(isFileExists(key)).map { case (folderId, isFileExists) ⇒
        if (isFileExists) throw StorageException.AlreadyExists(key)
        service.upload(folderId, key.name, inputStream)
      }
      future.failed.foreach(_ ⇒ inputStream.close())
      StorageUtils.wrapFuture(key, future.map(_ ⇒ StorageIOResult.Success(key, 0)))
    }

    blockingSink.named("gdriveWrite")
  }

  def delete = {
    Flow[Path]
      // .log("gdrive-delete")
      .mapAsync(1) { path ⇒
        filesCache -= path
        val future = getFileId(path)
          .map(fileId ⇒ service.delete(fileId))
          .map(_ ⇒ StorageIOResult.Success(path, 0))
        StorageUtils.wrapFuture(path, future)
      }
      .fold(Seq.empty[StorageIOResult])(_ :+ _)
      .map(StorageUtils.foldIOResults)
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

  private[this] def getFileId(path: Path) = {
    getFolderId(path.parent).map { folderId ⇒
      filesCache.getOrElseUpdate(path, service.files(folderId, path.name).head.id)
    }
  }

  private[this] def isFileExists(path: Path) = {
    getFileId(path)
      .map(_ ⇒ true)
      .recover { case _ ⇒ false }
  }
}
