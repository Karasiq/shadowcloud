package com.karasiq.shadowcloud.shell

import java.nio.file.{OpenOption, StandardOpenOption, Path ⇒ FSPath}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.language.postfixOps

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink}

import com.karasiq.shadowcloud.actors.RegionGC.GCStrategy
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.{File, Folder, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.GCReport

private[shell] object RegionContext {
  def apply(regionId: RegionId)(implicit context: ShellContext): RegionContext = {
    new RegionContext(regionId)
  }
}

private[shell] final class RegionContext(val regionId: RegionId)(implicit context: ShellContext) {
  import context._
  import sc.ops.supervisor

  def register(storage: StorageContext): Unit = {
    supervisor.register(regionId, storage.storageId)
  }

  def unregister(storage: StorageContext): Unit = {
    supervisor.unregister(regionId, storage.storageId)
  }

  def terminate(): Unit = {
    supervisor.deleteRegion(regionId)
  }

  def listDir(path: Path): Folder = {
    val future = sc.ops.region.getFolder(regionId, path)
    // ShellUtils.print(future)(ShellUtils.toStrings)
    await(future)
  }

  def createDir(path: Path): IndexDiff = {
    val future = sc.ops.region.createFolder(regionId, path)
    // ShellUtils.print(future)(diff ⇒ Array(s"Folder created: $path", diff.toString))
    await(future)
  }

  def deleteDir(path: Path): Folder = {
    val future = sc.ops.region.deleteFolder(regionId, path)
    // ShellUtils.print(future)(ShellUtils.toStrings)
    await(future)
  }

  def deleteFile(path: Path): Set[File] = {
    val future = sc.ops.region.deleteFiles(regionId, path)
    // ShellUtils.print(future)(_.map(ShellUtils.toString))
    await(future)
  }

  def upload(localPath: FSPath, path: Path): File = {
    val future = FileIO.fromPath(localPath)
      .via(sc.streams.metadata.writeFileAndMetadata(regionId, path))
      .map(_._1)
      .runWith(Sink.head)

    // file.foreach(file ⇒ println(ShellUtils.toString(file)))
    await(future)
  }

  def download(localPath: FSPath, path: Path): IOResult = {
    val options = Set[OpenOption](StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    val future = sc.streams.file.readMostRecent(regionId, path).runWith(FileIO.toPath(localPath, options))
    // ShellUtils.printIOResult(future)
    await(future)
  }

  def collectGarbage(delete: Boolean = false): GCReport = {
    await(sc.ops.region.collectGarbage(regionId, if (delete) GCStrategy.Delete else GCStrategy.Preview))
  }

  private[this] def await[T](future: Future[T]): T = {
    Await.result(future, Duration.Inf)
  }
}
