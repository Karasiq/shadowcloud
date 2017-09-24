package com.karasiq.shadowcloud.storage.gdrive

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

import akka.actor.ActorContext
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}

import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.utils.ByteStringOutputStream

private[gdrive] object GDriveRepository {
  def apply(service: GDriveService)(implicit ac: ActorContext): GDriveRepository = {
    new GDriveRepository(service)
  }
}

private[gdrive] class GDriveRepository(service: GDriveService)(implicit ac: ActorContext) extends PathTreeRepository {
  import ac.dispatcher // Blocking dispatcher
  private[this] val foldersCache = TrieMap.empty[Path, GDrive.Entity]

  def keys = {
    subKeys(Path.root)
  }

  override def subKeys(fromPath: Path) = {
    val future = Future(service.traverseFolder(fromPath.nodes))
    val ioFuture = StorageUtils.wrapFuture(fromPath, future.map(map ⇒ StorageIOResult.Success(fromPath, map.size)))
    Source.fromFuture(future)
      .mapConcat(_.map { case (pathNodes, entities) ⇒
        val parent = Path(pathNodes.toVector)
        entities.map(e ⇒ parent / e.name)
      })
      .mapConcat(_.toVector)
      .mapMaterializedValue(_ ⇒ ioFuture)
  }

  def read(key: Path) = {
    val future = getFile(key).map { file ⇒
      val outputStream = ByteStringOutputStream()
      service.download(file.id, outputStream)
      outputStream.toByteString
    }

    val resultFuture = StorageUtils.wrapFuture(key, future.map(bytes ⇒ StorageIOResult.Success(key, bytes.length)))
    Source.fromFuture(future)
      .mapMaterializedValue(_ ⇒ resultFuture)
  }

  def write(key: Path) = {
    StreamConverters.asInputStream().mapMaterializedValue { inputStream ⇒
      val future = getFolder(key.parent).map { folder ⇒
        if (service.fileExists(folder.id, key.name)) throw StorageException.AlreadyExists(key)
        service.upload(folder.id, key.name, inputStream)
      }
      future.onComplete(_ ⇒ inputStream.close())
      future
        .map(_ ⇒ StorageIOResult.Success(key, 0))
        .recover { case error ⇒ StorageIOResult.Failure(key, StorageUtils.wrapException(key, error)) }
    }
  }

  def delete = {
    Flow[Path]
      .mapAsync(1) { path ⇒
        getFile(path)
          .map(file ⇒ service.delete(file.id))
          .map(_ ⇒ StorageIOResult.Success(path, 0))
          .recover { case error ⇒ StorageIOResult.Failure(path, StorageUtils.wrapException(path, error)) }
      }
      .fold(Seq.empty[StorageIOResult])(_ :+ _)
      .map(StorageUtils.foldIOResults)
      .recover { case error ⇒ StorageIOResult.Failure(Path.root, StorageUtils.wrapException(Path.root, error)) }
      .toMat(Sink.head)(Keep.right)
  }

  private[this] def getFolder(path: Path) = {
    Future(foldersCache.getOrElseUpdate(path, service.folder(path.nodes)))
  }

  private[this] def getFile(path: Path) = {
    getFolder(path.parent)
      .map(folder ⇒ service.files(folder.id, path.name).head)
  }
}
