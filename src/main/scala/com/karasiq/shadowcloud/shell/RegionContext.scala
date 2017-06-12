package com.karasiq.shadowcloud.shell

import java.nio.file.{StandardOpenOption, Path => FSPath}

import scala.concurrent.Future
import scala.language.postfixOps

import akka.stream.scaladsl.FileIO

import com.karasiq.shadowcloud.index.{Folder, Path}

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
    supervisor.unregister(regionId,  storage.storageId)
  }

  def terminate(): Unit = {
    supervisor.deleteRegion(regionId)
  }

  def getDir(path: Path): Future[Folder] = {
    sc.ops.region.getFolder(regionId, path)
  }

  def listDir(path: Path): Unit = {
    ShellUtils.print(getDir(path))(ShellUtils.toStrings)
  }

  def createDir(path: Path): Unit = {
    ShellUtils.print(sc.ops.region.createFolder(regionId, path)) { _ ⇒
      Array(s"Folder created: $path")
    }
  }

  def deleteDir(path: Path): Unit = {
    ShellUtils.print(sc.ops.region.deleteFolder(regionId, path))(ShellUtils.toStrings)
  }

  def deleteFile(path: Path): Unit = {
    ShellUtils.print(sc.ops.region.deleteFiles(regionId, path))(_.map(ShellUtils.toString))
  }

  def upload(localPath: FSPath, path: Path): Unit = {
    val file = FileIO.fromPath(localPath).runWith(sc.streams.file.write(regionId, path))
    file.foreach(file ⇒ println(ShellUtils.toString(file)))
  }

  def download(localPath: FSPath, path: Path): Unit = {
    val options = Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    val future = sc.streams.file.readMostRecent(regionId, path).runWith(FileIO.toPath(localPath, options))
    ShellUtils.printIOResult(future)
  }
}
