package com.karasiq.shadowcloud.drive

import java.io.IOException

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, PossiblyHarmful, Props, Terminated}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.{File, Folder, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.{RegionHealth, StorageHealth}

object VirtualFSDispatcher {
  // Messages
  sealed trait Message
  final case class GetFile(path: Path) extends Message
  object GetFile extends MessageStatus[Path, File]

  final case class GetFolder(path: Path) extends Message
  object GetFolder extends MessageStatus[Path, Folder]

  final case class CreateFile(path: Path) extends Message
  object CreateFile extends MessageStatus[Path, File]

  final case class CreateFolder(path: Path) extends Message
  object CreateFolder extends MessageStatus[Path, Folder]

  final case class DeleteFile(path: Path) extends Message
  object DeleteFile extends MessageStatus[Path, File]

  final case class DeleteFolder(path: Path) extends Message
  object DeleteFolder extends MessageStatus[Path, Folder]

  final case class GetHealth(path: Path) extends Message
  object GetHealth extends MessageStatus[Path, StorageHealth]

  final case class ReleaseFile(path: Path) extends Message
  object ReleaseFile extends MessageStatus[Path, File]

  final case class RenameFile(path: Path, newPath: Path) extends Message
  object RenameFile extends MessageStatus[Path, File]

  final case class DispatchIOOperation(path: Path, operation: FileIOScheduler.Message) extends Message
  object DispatchIOOperation extends MessageStatus[Path, Any]

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private final case class OpenFile(path: Path, file: File) extends InternalMessage
  private object OpenFile extends MessageStatus[Path, File]
  private final case class CloseFile(path: Path, dispatcher: ActorRef) extends InternalMessage

  // Events
  sealed trait Event

  // Props
  def props(config: SCDriveConfig): Props = {
    Props(new VirtualFSDispatcher(config))
  }

  private implicit class VirtualPathOps(private val path: Path) extends AnyVal {
    def isRegionRoot: Boolean = {
      path.nodes.length == 1
    }

    def regionAndPath: (RegionId, Path) = {
      (path.nodes.head, Path(path.nodes.drop(1)))
    }
  }
}

class VirtualFSDispatcher(config: SCDriveConfig) extends Actor with ActorLogging {
  import VirtualFSDispatcher._
  private[this] implicit lazy val sc = ShadowCloud()
  private[this] implicit val ec: ExecutionContext = context.dispatcher
  private[this] implicit val timeout = Timeout(config.fileIO.timeout)

  object state {
    val fileWrites = mutable.AnyRefMap.empty[Path, ActorRef]
  }

  object operations {
    def getFile(path: Path): Future[File] = {
      if (path.isRoot || path.isRegionRoot) {
        Future.failed(StorageException.NotFound(path))
      } else if (state.fileWrites.contains(path)) {
        FileIOScheduler.GetCurrentRevision.unwrapFuture(state.fileWrites(path) ? FileIOScheduler.GetCurrentRevision)
      } else {
        val (regionId, regionPath) = path.regionAndPath
        sc.ops.region.getFiles(regionId, regionPath)
          .map(FileVersions.mostRecent)
      }
    }

    def getFolder(path: Path): Future[Folder] = {
      if (path.isRoot) {
        // List region IDs
        sc.ops.supervisor.getSnapshot()
          .map(ss ⇒ Folder(path, folders = ss.regions.keySet))
      } else {
        val (regionId, regionPath) = path.regionAndPath
        sc.ops.region.getFolder(regionId, regionPath)
      }
    }

    def openFileForWrite(path: Path): Future[File] = {
      if (state.fileWrites.contains(path)) {
        FileIOScheduler.GetCurrentRevision.unwrapFuture(state.fileWrites(path) ? FileIOScheduler.GetCurrentRevision)
      } else {
        val (regionId, regionPath) = path.regionAndPath
        for {
          oldFiles ← sc.ops.region.getFiles(regionId, regionPath)
          oldFile = FileVersions.mostRecent(oldFiles)
          newFile ← OpenFile.unwrapFuture(self ? OpenFile(path, oldFile))
        } yield newFile
      }
    }

    def createFile(path: Path): Future[File] = {
      if (path.isRoot || path.isRegionRoot || state.fileWrites.contains(path)) {
        Future.failed(StorageException.AlreadyExists(path))
      } else {
        val (_, regionPath) = path.regionAndPath
        val newFile = File(regionPath)
        OpenFile.unwrapFuture(self ? OpenFile(path, newFile))
      }
    }

    def createFolder(path: Path): Future[Folder] = {
      if (path.isRoot || path.isRegionRoot) {
        Future.failed(StorageException.AlreadyExists(path))
      } else {
        val (regionId, regionPath) = path.regionAndPath
        sc.ops.region.createFolder(regionId, regionPath)
          .flatMap(_ ⇒ sc.ops.region.getFolder(regionId, regionPath))
      }
    }

    def deleteFile(path: Path): Future[File] = {
      if (path.isRoot || path.isRegionRoot) {
        Future.failed(StorageException.NotFound(path))
      } else {
        val (regionId, regionPath) = path.regionAndPath
        state.fileWrites.remove(path).foreach(context.stop)
        sc.ops.region.deleteFiles(regionId, regionPath).map(FileVersions.mostRecent)
      }
    }

    def deleteFolder(path: Path): Future[Folder] = {
      if (path.isRoot || path.isRegionRoot) {
        Future.failed(StorageException.NotFound(path))
      } else {
        val (regionId, regionPath) = path.regionAndPath
        sc.ops.region.deleteFolder(regionId, regionPath)
      }
    }

    def syncFile(path: Path): Future[File] = {
      state.fileWrites.get(path) match {
        case Some(dispatcher) ⇒
          FileIOScheduler.ReleaseFile.unwrapFuture(dispatcher ? FileIOScheduler.ReleaseFile)
          
        case None ⇒
          Future.failed(StorageException.IOFailure(path, new IOException("Not opened for write")))
      }
    }

    def closeFile(path: Path): Unit = {
      state.fileWrites.remove(path).foreach(_ ! PoisonPill)
    }

    def renameFile(path: Path, newPath: Path): Future[File] = {
      if (path.isRoot || path.isRegionRoot || newPath.isRoot || newPath.isRegionRoot)
        return Future.failed(StorageException.NotFound(path))

      val (regionId, oldRegionPath) = path.regionAndPath
      val (regionId2, newRegionPath) = path.regionAndPath

      if (regionId != regionId2)
        return Future.failed(StorageException.IOFailure(path, new IOException("Regions id should match")))

      if (state.fileWrites.contains(path)) {
        for {
          oldFile ← syncFile(path)
          newFile ← sc.ops.region.createFile(regionId, oldFile.copy(path = newRegionPath))
          _ ← sc.ops.region.deleteFiles(regionId, oldRegionPath)
        } yield newFile
      } else {
        for {
          oldFiles ← sc.ops.region.getFiles(regionId, oldRegionPath)
          oldFile = FileVersions.mostRecent(oldFiles)
          newFile ← sc.ops.region.createFile(regionId, oldFile.copy(path = newRegionPath))
          _ ← sc.ops.region.deleteFiles(regionId, oldRegionPath)
        } yield newFile
      }
    }
  }

  override def receive: Receive = {
    case GetFile(path) ⇒
      val future = operations.getFile(path)
      GetFile.wrapFuture(path, future).pipeTo(sender())

    case GetFolder(path) ⇒
      val future = operations.getFolder(path)
      GetFolder.wrapFuture(path, future).pipeTo(sender())

    case CreateFile(path) ⇒
      CreateFile.wrapFuture(path, operations.createFile(path)).pipeTo(sender())

    case CreateFolder(path) ⇒
      CreateFolder.wrapFuture(path, operations.createFolder(path)).pipeTo(sender())

    case DeleteFile(path) ⇒
      DeleteFile.wrapFuture(path, operations.deleteFile(path)).pipeTo(sender())

    case DeleteFolder(path) ⇒
      DeleteFolder.wrapFuture(path, operations.deleteFolder(path)).pipeTo(sender())

    case OpenFile(path, file) ⇒
      val (regionId, _) = path.regionAndPath
      if (!state.fileWrites.contains(path)) {
        val dispatcher = context.actorOf(FileIOScheduler.props(config, regionId, file))
        state.fileWrites(path) = dispatcher
        context.watchWith(dispatcher, CloseFile(path, dispatcher))
      }
      OpenFile.wrapFuture(path, FileIOScheduler.GetCurrentRevision.unwrapFuture(state.fileWrites(path) ? FileIOScheduler.GetCurrentRevision))
        .pipeTo(sender())

    case CloseFile(path, dispatcher) ⇒
      if (state.fileWrites.get(path).contains(dispatcher)) state.fileWrites -= path

    case ReleaseFile(path) ⇒
      val future = operations.syncFile(path)
      future.onComplete(_.foreach(_ ⇒ operations.closeFile(path)))
      ReleaseFile.wrapFuture(path, future).pipeTo(sender())

    case RenameFile(path, newPath) ⇒
      RenameFile.wrapFuture(path, operations.renameFile(path, newPath)).pipeTo(sender())

    case msg @ DispatchIOOperation(path, operation) ⇒
      val currentSender = sender()
      state.fileWrites.get(path) match {
        case Some(dispatcher) ⇒
          DispatchIOOperation.wrapFuture(path, dispatcher ? operation)
            .pipeTo(currentSender)

        case None ⇒
          operations.openFileForWrite(path).onComplete {
            case Success(_) ⇒ self.tell(msg, currentSender) // Retry
            case Failure(exc) ⇒ currentSender ! DispatchIOOperation.Failure(path, exc)
          }
      }

    case GetHealth(path) ⇒
      if (path.isRoot) {
        val allHealths = sc.ops.supervisor.getSnapshot().flatMap { ss ⇒
          val storages = ss.storages.keys.toSeq
          val futures = storages.map(storageId ⇒ sc.ops.storage.getHealth(storageId))
          Future.sequence(futures)
        }
        val mergedHealth = allHealths.map(_.foldLeft(StorageHealth.empty)(_ + _))
        GetHealth.wrapFuture(path, mergedHealth).pipeTo(sender())
      } else {
        val (regionId, _) = path.regionAndPath
        GetHealth.wrapFuture(path, sc.ops.region.getHealth(regionId).map(_.toStorageHealth)).pipeTo(sender())
      }
  }
}
