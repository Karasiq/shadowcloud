package com.karasiq.shadowcloud.shell

import java.nio.file.{StandardOpenOption, Path => FSPath}

import akka.stream.scaladsl.FileIO
import com.karasiq.shadowcloud.actors.RegionSupervisor.{DeleteRegion, RegisterStorage, UnregisterStorage}
import com.karasiq.shadowcloud.index.Path

import scala.language.postfixOps

private[shell] object RegionContext {
  def apply(region: String)(implicit context: ShellContext): RegionContext = {
    new RegionContext(region)
  }
}

private[shell] final class RegionContext(val regionId: String)(implicit context: ShellContext) {
  import context._

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
    ShellUtils.print(regionOps.getFolder(regionId, path))(ShellUtils.toStrings)
  }

  def createDir(path: Path): Unit = {
    ShellUtils.print(regionOps.createFolder(regionId, path)) { _ ⇒
      Array(s"Folder created: $path")
    }
  }

  def deleteFolder(path: Path): Unit = {
    ShellUtils.print(regionOps.deleteFolder(regionId, path))(ShellUtils.toStrings)
  }

  def deleteFile(path: Path): Unit = {
    ShellUtils.print(regionOps.deleteFiles(regionId, path))(_.map(ShellUtils.toString))
  }

  def upload(localPath: FSPath, path: Path): Unit = {
    val file = FileIO.fromPath(localPath).runWith(fileStreams.write(regionId, path))
    file.foreach(file ⇒ println(ShellUtils.toString(file)))
  }

  def download(localPath: FSPath, path: Path): Unit = {
    val options = Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    val future = fileStreams.read(regionId, path).runWith(FileIO.toPath(localPath, options))
    ShellUtils.printIOResult(future)
  }
}
