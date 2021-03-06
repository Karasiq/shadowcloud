package com.karasiq.shadowcloud.storage.files

import java.io.FileNotFoundException
import java.nio.file.{Files, StandardOpenOption, Path => FSPath}

import akka.NotUsed
import akka.stream.scaladsl.{FileIO, Flow, Keep, Sink, Source}
import akka.stream.{ActorAttributes, Attributes, Materializer}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

private[storage] object FileRepository {
  def apply(folder: FSPath)(implicit ec: ExecutionContext, mat: Materializer): FileRepository = {
    new FileRepository(folder)
  }

  private def toSCPath(path: FSPath): Path = {
    val nodes = path
      .iterator().asScala
      .map(_.getFileName.toString)
      .toVector

    Path(nodes)
  }
}

/**
  * Uses local filesystem to store data
  * @param rootFolder Root directory
  */
private[storage] final class FileRepository(rootFolder: FSPath)(implicit ec: ExecutionContext, mat: Materializer) extends PathTreeRepository {
  def read(key: Path): Source[Data, Result] = {
    val path = toRealPath(key)
    FileIO.fromPath(path).mapMaterializedValue(StorageUtils.wrapAkkaIOFuture(FileRepository.toSCPath(path), _))
  }

  def write(key: Path): Sink[Data, Result] = {
    val path = toRealPath(key)
    val parentDir = path.getParent
    if (!Files.isDirectory(parentDir)) Files.createDirectories(parentDir)
    FileIO.toPath(path, Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
      .mapMaterializedValue(StorageUtils.wrapAkkaIOFuture(path.toString, _))
  }

  def delete: Sink[Path, Result] = {
    Flow[Path]
      .map(toRealPath)
      .map(file ⇒ (file, Files.size(file)))
      .withAttributes(Attributes.name("filePreDelete") and ActorAttributes.IODispatcher)
      .alsoTo(Flow[(FSPath, Long)].map(_._1).to(fileDeleteSink))
      .map(_._2)
      .fold(0L)(_ + _)
      .map(StorageIOResult.Success(FileRepository.toSCPath(rootFolder), _))
      .toMat(Sink.head)(Keep.right)
  }

  def subKeys(fromPath: Path): Source[Path, Result] = {
    val subDirPath = toRealPath(fromPath)
    FileSystemUtils.walkFileTree(subDirPath, includeDirs = false)
      .recoverWithRetries(1, { case _: FileNotFoundException ⇒ Source.empty })
      // .log("file-repository-tree")
      .map(fsPath ⇒ toVirtualPath(fsPath).toRelative(fromPath))
      .mapMaterializedValue(_ ⇒ Future.successful(StorageIOResult.Success(FileRepository.toSCPath(rootFolder) / fromPath, 0)))
  }

  private[this] def toRealPath(relPath: Path): FSPath = {
    relPath.nodes.foldLeft(rootFolder) { (path, node) ⇒
      require(FileSystemUtils.isValidFileName(node), s"File name not supported: $node")
      path.resolve(node)
    }
  }

  private[this] def toVirtualPath(fsPath: FSPath): Path = {
    FileRepository.toSCPath(rootFolder.relativize(fsPath))
  }

  private[this] val fileDeleteSink: Sink[FSPath, NotUsed] = Sink.foreach(Files.delete(_: FSPath))
    .withAttributes(Attributes.name("fileDelete") and ActorAttributes.IODispatcher)
    .mapMaterializedValue(_ ⇒ NotUsed)
}
