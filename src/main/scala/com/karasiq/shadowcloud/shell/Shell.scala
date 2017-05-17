package com.karasiq.shadowcloud.shell

import java.nio.file.Files

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.postfixOps

import com.karasiq.shadowcloud.actors.RegionSupervisor
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.storage.props.StorageProps

object Shell extends ImplicitConversions {
  private[this] implicit val context = ShellContext()
  import context._
  import sc.actors.regionSupervisor

  def openRegion(regionId: String): RegionContext = {
    RegionContext(regionId)
  }

  def createRegion(regionId: String): RegionContext = {
    regionSupervisor ! RegionSupervisor.AddRegion(regionId, RegionConfig.forId(regionId, actorSystem.settings.config.getConfig("shadowcloud")))
    openRegion(regionId)
  }

  def openStorage(storageId: String): StorageContext = {
    StorageContext(storageId)
  }

  def createStorage(storageId: String, props: StorageProps): StorageContext = {
    regionSupervisor ! RegionSupervisor.AddStorage(storageId, props)
    openStorage(storageId)
  }

  def createTempStorage(storageId: String): StorageContext = {
    createStorage(storageId, StorageProps.fromDirectory(Files.createTempDirectory("sc-shell")))
  }

  def quit(): Unit = {
    Await.result(actorSystem.terminate(), Duration.Inf)
    sys.exit()
  }

  private[this] val tempDirectory = {
    sys.props
      .get("shadowcloud.temp-storage-dir")
      .fold(Files.createTempDirectory("sc-shell"))(toFSPath)
  }
  val testRegion = createRegion("test")
  val testStorage = createStorage("test", StorageProps.fromDirectory(tempDirectory))
  testRegion.register(testStorage)

  def test(): Unit = {
    testRegion.upload("LICENSE", "LICENSE")
    Thread.sleep(5000)
    Files.deleteIfExists("LICENSE_remote")
    testRegion.download("LICENSE_remote", "LICENSE")
    Thread.sleep(5000)
    testRegion.deleteFile("LICENSE")
  }
}
