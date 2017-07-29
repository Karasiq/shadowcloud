package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, StandardOpenOption, Path ⇒ FSPath}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.stream.{ActorAttributes, Attributes, Materializer}
import akka.stream.scaladsl.{FileIO, Sink, Source}

import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.utils.FileSystemUtils

private[storage] object FileRepository {
  def apply(folder: FSPath)(implicit ec: ExecutionContext, mat: Materializer): FileRepository = {
    new FileRepository(folder)
  }
}

/**
  * Uses local filesystem to store data
  * @param folder Root directory
  */
private[storage] final class FileRepository(folder: FSPath)(implicit ec: ExecutionContext, mat: Materializer) extends PathTreeRepository {
  def read(key: Path): Source[Data, Result] = {
    val path = toAbsolute(key)
    FileIO.fromPath(path)
      .mapMaterializedValue(StorageUtils.wrapIOResult(path.toString, _))
  }

  def keys: Source[Path, Result] = {
    subKeys(Path.root)
  }

  def delete(key: Path): Result = {
    val file = toAbsolute(key)
    ioOperation(file.toString, { () ⇒
      val size = Files.size(file)
      Files.delete(file)
      size
    })
  }

  def write(key: Path): Sink[Data, Result] = {
    val path = toAbsolute(key)
    Files.createDirectories(path.getParent)
    FileIO.toPath(path, Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
      .mapMaterializedValue(StorageUtils.wrapIOResult(path.toString, _))
  }

  override def subKeys(fromPath: Path): Source[Path, Result] = {
    val subDirPath = toAbsolute(fromPath)
    FileSystemUtils.walkFileTree(subDirPath, includeDirs = false)
      .map(toRelative)
      .mapMaterializedValue(_ ⇒ Future.successful(StorageIOResult.Success(subDirPath.toString, 0)))
  }

  private[this] def toAbsolute(path: Path): FSPath = {
    path.nodes.foldLeft(folder)((p, n) ⇒ p.resolve(n))
  }

  private[this] def toRelative(fsPath: FSPath): Path = {
    Path(folder.relativize(fsPath).iterator().asScala.map(_.getFileName.toString).toVector)
  }

  private[this] def ioOperation(path: String, f: () ⇒ Long): Future[StorageIOResult] = {
    Source.single(path)
      .map(path ⇒ StorageIOResult.Success(path, f()))
      .addAttributes(Attributes.name("ioOperation") and ActorAttributes.IODispatcher)
      .runWith(Sink.head)
  }
}
