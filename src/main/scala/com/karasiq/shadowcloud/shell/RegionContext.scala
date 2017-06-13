package com.karasiq.shadowcloud.shell

import java.nio.file.{StandardOpenOption, Path ⇒ FSPath}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.postfixOps

import akka.stream.IOResult
import akka.stream.scaladsl.FileIO

import com.karasiq.shadowcloud.index.{File, Folder, Path}
import com.karasiq.shadowcloud.index.diffs.IndexDiff

private[shell] object RegionContext {
  def apply(region: String)(implicit context: ShellContext): RegionContext = {
    new RegionContext(region)
  }
}

private[shell] final class RegionContext(val regionId: String)(implicit context: ShellContext) {

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
    Await.result(future, Duration.Inf)
  }

  def createDir(path: Path): IndexDiff = {
    val future = sc.ops.region.createFolder(regionId, path)
    // ShellUtils.print(future)(diff ⇒ Array(s"Folder created: $path", diff.toString))
    Await.result(future, Duration.Inf)
  }

  def deleteDir(path: Path): Folder = {
    val future = sc.ops.region.deleteFolder(regionId, path)
    // ShellUtils.print(future)(ShellUtils.toStrings)
    Await.result(future, Duration.Inf)
  }

  def deleteFile(path: Path): Set[File] = {
    val future = sc.ops.region.deleteFiles(regionId, path)
    // ShellUtils.print(future)(_.map(ShellUtils.toString))
    Await.result(future, Duration.Inf)
  }

  def upload(localPath: FSPath, path: Path): File = {
    val future = FileIO.fromPath(localPath).runWith(sc.streams.file.write(regionId, path))
    // file.foreach(file ⇒ println(ShellUtils.toString(file)))
    Await.result(future, Duration.Inf)
  }

  def download(localPath: FSPath, path: Path): IOResult = {
    val options = Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    val future = sc.streams.file.readMostRecent(regionId, path).runWith(FileIO.toPath(localPath, options))
    // ShellUtils.printIOResult(future)
    Await.result(future, Duration.Inf)
  }
}
