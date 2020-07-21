package com.karasiq.shadowcloud.drive.fuse

import java.nio.file.{Files, Paths}

import akka.Done
import akka.actor.ActorSystem
import com.karasiq.shadowcloud.drive.SCDrive

import scala.concurrent.Future

object SCFuseHelper {
  def mount()(implicit actorSystem: ActorSystem): Future[Done] = {
    import com.karasiq.common.configs.ConfigImplicits._

    // Init FUSE file system
    val drive               = SCDrive(actorSystem)
    val fuseConfig          = drive.config.rootConfig.getConfigIfExists("fuse")
    implicit val dispatcher = actorSystem.dispatchers.lookup("shadowcloud.drive.fuse.default-dispatcher")

    // Fix FUSE properties
    val fuseWinFspDll = {
      val paths = fuseConfig.getStrings("winfsp.dll-paths")
      paths.find(p ⇒ Files.isRegularFile(Paths.get(p)))
    }
    fuseWinFspDll.foreach(path ⇒ System.setProperty("jnrfuse.winfsp.path", path))

    if (fuseConfig.withDefault(false, _.getBoolean("winfsp.fix-utf8"))) {
      System.setProperty("file.encoding", "UTF-8")
    }

    val fileSystem  = SCFileSystem(drive.config, drive.dispatcher, actorSystem)
    val mountFuture = SCFileSystem.mountInSeparateThread(fileSystem)
    mountFuture.failed.foreach(actorSystem.log.error(_, "FUSE filesystem mount failed"))
    sys.addShutdownHook(fileSystem.umount())
    mountFuture
  }
}
