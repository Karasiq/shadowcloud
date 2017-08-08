package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, StandardOpenOption, Path ⇒ FSPath}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes, Materializer}
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}

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
  * @param rootFolder Root directory
  */
private[storage] final class FileRepository(rootFolder: FSPath)(implicit ec: ExecutionContext, mat: Materializer) extends PathTreeRepository {
  def keys: Source[Path, Result] = {
    subKeys(Path.root)
  }

  def read(key: Path): Source[Data, Result] = {
    val path = toRealPath(key)
    FileIO.fromPath(path).mapMaterializedValue(StorageUtils.wrapIOResult(path.toString, _))
  }

  def write(key: Path): Sink[Data, Result] = {
    val path = toRealPath(key)
    val parentDir = path.getParent
    if (!Files.isDirectory(parentDir)) Files.createDirectories(parentDir)
    FileIO.toPath(path, Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
      .mapMaterializedValue(StorageUtils.wrapIOResult(path.toString, _))
  }

  def delete: Sink[Path, Result] = {
    Flow[Path]
      .map(toRealPath)
      .map(file ⇒ (file, Files.size(file)))
      .addAttributes(Attributes.name("filePreDelete") and ActorAttributes.IODispatcher)
      .alsoTo(Flow[(FSPath, Long)].map(_._1).to(fileDeleteSink))
      .map(_._2)
      .fold(0L)(_ + _)
      .map(StorageIOResult.Success(rootFolder.toString, _))
      .toMat(Sink.head)(Keep.right)
  }

  override def subKeys(fromPath: Path): Source[Path, Result] = {
    val subDirPath = toRealPath(fromPath)
    FileSystemUtils.walkFileTree(subDirPath, includeDirs = false)
      // .log("file-repository-tree")
      .map(fsPath ⇒ toVirtualPath(fsPath).toRelative(fromPath))
      .mapMaterializedValue(_ ⇒ Future.successful(StorageIOResult.Success(subDirPath.toString, 0)))
  }

  private[this] def toRealPath(path: Path): FSPath = {
    path.nodes.foldLeft(rootFolder) { (path, node) ⇒
      require(FileSystemUtils.isValidFileName(node), "File name not supported: " + node)
      path.resolve(node)
    }
  }

  private[this] def toVirtualPath(fsPath: FSPath): Path = {
    val nodes = rootFolder.relativize(fsPath)
      .iterator().asScala
      .map(_.getFileName.toString)
    Path(nodes.toVector)
  }

  private[this] val fileDeleteSink: Sink[FSPath, NotUsed] = Sink.foreach(Files.delete)
    .addAttributes(Attributes.name("fileDelete") and ActorAttributes.IODispatcher)
    .mapMaterializedValue(_ ⇒ NotUsed)
}
