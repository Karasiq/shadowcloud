package com.karasiq.shadowcloud.shell

import java.nio.file.{StandardOpenOption, Path => FSPath}

import scala.language.postfixOps

import akka.stream.scaladsl.FileIO

import com.karasiq.shadowcloud.actors.RegionSupervisor.{DeleteRegion, RegisterStorage, UnregisterStorage}
import com.karasiq.shadowcloud.index.Path

private[shell] object RegionContext {
  def apply(region: String)(implicit context: ShellContext): RegionContext = {
    new RegionContext(region)
  }
}

private[shell] final class RegionContext(val regionId: String)(implicit context: ShellContext) {
  import context._
  import sc.actors.regionSupervisor

  def register(storage: StorageContext): Unit = {
    regionSupervisor ! RegisterStorage(regionId, storage.storageId)
  }

  def unregister(storage: StorageContext): Unit = {
    regionSupervisor ! UnregisterStorage(regionId,  storage.storageId)
  }

  def terminate(): Unit = {
    regionSupervisor ! DeleteRegion(regionId)
  }

  def list(path: Path): Unit = {
    ShellUtils.print(sc.ops.region.getFolder(regionId, path))(ShellUtils.toStrings)
  }

  def createDir(path: Path): Unit = {
    ShellUtils.print(sc.ops.region.createFolder(regionId, path)) { _ ⇒
      Array(s"Folder created: $path")
    }
  }

  def deleteFolder(path: Path): Unit = {
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
    val future = sc.streams.file.read(regionId, path).runWith(FileIO.toPath(localPath, options))
    ShellUtils.printIOResult(future)
  }
}
