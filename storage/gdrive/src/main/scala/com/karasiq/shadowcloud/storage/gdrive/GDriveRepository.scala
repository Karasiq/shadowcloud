package com.karasiq.shadowcloud.storage.gdrive

import java.io.{BufferedInputStream, BufferedOutputStream, FileNotFoundException}

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.NotUsed
import akka.actor.ActorContext
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}

import com.karasiq.common.memory.SizeUnit
import com.karasiq.gdrive.files.{GDrive, GDriveService}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils

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
    /* def loadFileBytes() = {
      Source.single(key)
        .mapAsync(1)(key ⇒ getFile(key).map { file ⇒
          val outputStream = ByteStringOutputStream()
          service.download(file.get.id, outputStream)
          outputStream.toByteString
        })
        .alsoToMat(Sink.head)(Keep.right)
        .mapMaterializedValue(future ⇒ StorageUtils.wrapFuture(key, future.map(bytes ⇒ StorageIOResult.Success(key, bytes.length))))
    } */

    def loadFileBytesBlocking() = {
      StreamConverters.asOutputStream(15 seconds).mapMaterializedValue { outputStream ⇒
        val bufferedOutputStream = new BufferedOutputStream(outputStream, SizeUnit.MB.intValue)
        val future = getFile(key).map(fo ⇒ fo.foreach(file ⇒ service.download(file.id, bufferedOutputStream)))
        future.onComplete(_ ⇒ bufferedOutputStream.close())
        StorageUtils.wrapFuture(key, future.map(_ ⇒ StorageIOResult.Success(key, 0)))
      }
    }

    loadFileBytesBlocking().named("gdriveRead")
  }

  def write(key: Path) = {
    /* val concatSink = Flow[ByteString]
      .via(ByteStreams.concat)
      .flatMapConcat { bytes ⇒
        val future = getFolder(key.parent).map { folder ⇒
          if (service.fileExists(folder.id, key.name)) throw StorageException.AlreadyExists(key)
          service.upload(folder.id, key.name, ByteStringInputStream(bytes))
        }
        Source.fromFuture(future).map(_ ⇒ bytes)
      }
      .map(bytes ⇒ StorageIOResult.Success(key, bytes.length))
      .toMat(Sink.head)(Keep.right)
      .mapMaterializedValue(future ⇒ StorageUtils.wrapFuture(key, future.map(_ ⇒ StorageIOResult.Success(key, 0)))) */
    
    val blockingSink = StreamConverters.asInputStream(15 seconds).mapMaterializedValue { inputStream ⇒
      val bufferedInputStream = new BufferedInputStream(inputStream, SizeUnit.MB.intValue)
      val future = createFolder(key.parent).map { folder ⇒
        if (service.fileExists(folder.id, key.name)) throw StorageException.AlreadyExists(key)
        service.upload(folder.id, key.name, bufferedInputStream)
      }
      future.failed.foreach(_ ⇒ bufferedInputStream.close())
      StorageUtils.wrapFuture(key, future.map(_ ⇒ StorageIOResult.Success(key, 0)))
    }

    blockingSink.named("gdriveWrite")
  }

  def delete = {
    Flow[Path]
      // .log("gdrive-delete")
      .mapAsync(1) { path ⇒
        val future = getFile(path)
          .map(fo ⇒ fo.foreach(file ⇒ service.delete(file.id)))
          .map(_ ⇒ StorageIOResult.Success(path, 0))
        StorageUtils.wrapFuture(path, future)
      }
      .fold(Seq.empty[StorageIOResult])(_ :+ _)
      .map(StorageUtils.foldIOResults)
      .recover { case error ⇒ StorageIOResult.Failure(Path.root, StorageUtils.wrapException(Path.root, error)) }
      .orElse(Source.single(StorageIOResult.Success(Path.root, 0)))
      .toMat(Sink.head)(Keep.right)
  }

  private[this] def createFolder(path: Path) = {
    Future(foldersCache.getOrElseUpdate(path, service.createFolder(path.nodes)))
  }

  private[this] def getFolder(path: Path) = {
    Future(foldersCache.getOrElseUpdate(path, service.folder(path.nodes)))
  }

  private[this] def getFile(path: Path) = {
    getFolder(path.parent)
      .map(folder ⇒ service.files(folder.id, path.name).headOption)
  }
}
