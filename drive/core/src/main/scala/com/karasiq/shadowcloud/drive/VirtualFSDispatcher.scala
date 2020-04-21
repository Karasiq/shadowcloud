package com.karasiq.shadowcloud.drive

import java.io.IOException

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, PossiblyHarmful, Props}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.{ActorState, MessageStatus}
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.index.diffs.FolderIndexDiff
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.model.{File, Folder, Path, RegionId}

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

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

  final case class RenameFile(path: Path, newPath: Path) extends Message
  object RenameFile extends MessageStatus[Path, File]

  final case class RenameFolder(path: Path, newPath: Path) extends Message
  object RenameFolder extends MessageStatus[Path, Folder]

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

// TODO: Tests
class VirtualFSDispatcher(config: SCDriveConfig) extends Actor with ActorLogging {
  import VirtualFSDispatcher._
  private[this] implicit lazy val sc = ShadowCloud()
  private[this] implicit val ec: ExecutionContext = context.dispatcher
  private[this] implicit val timeout = Timeout(config.fileIO.timeout)

  object state {
    val fileWrites = mutable.AnyRefMap.empty[Path, ActorRef]
  }

  object operations {
    def getCurrentRevision(actor: ActorRef): Future[File] = {
      val future = actor.ask(FileIOScheduler.GetCurrentRevision)(timeout = sc.implicits.defaultTimeout)
      FileIOScheduler.GetCurrentRevision.unwrapFuture(future)
    }
    
    def getCurrentRevision(path: Path): Future[File] = {
      getCurrentRevision(state.fileWrites(path))
    }
  
    def getFile(path: Path): Future[File] = {
      if (path.isRoot || path.isRegionRoot) {
        Future.failed(StorageException.NotFound(path))
      } else if (state.fileWrites.contains(path)) {
        getCurrentRevision(path)
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
          .map(ss ⇒ Folder(path, folders = ss.regions.filter(_._2.actorState.isInstanceOf[ActorState.Active]).keySet))
      } else {
        val (regionId, regionPath) = path.regionAndPath
        val virtualFiles = state.fileWrites
          .filter(_._1.parent == path)
          .map { case (_, actor) ⇒ operations.getCurrentRevision(actor).map(Some(_)).recover { case _ ⇒ None } }
          .toVector

        for {
          folder ← sc.ops.region.getFolder(regionId, regionPath)
          files ← Future.sequence(virtualFiles)
          fileNameSet = files.flatten.map(_.path.name).toSet
        } yield folder.copy(files = folder.files.filterNot(f ⇒ fileNameSet.contains(f.path.name)) ++ files.flatten)
      }
    }

    def openFileForWrite(path: Path): Future[File] = {
      if (state.fileWrites.contains(path)) {
        operations.getCurrentRevision(path)
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
      if (path.isRoot || path.isRegionRoot) {
        Future.failed(StorageException.AlreadyExists(path))
      } else {
        state.fileWrites.remove(path).foreach(_ ! PoisonPill)
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
        val deletedFile = state.fileWrites.remove(path).map { dsp ⇒
          operations.getCurrentRevision(dsp)
            .map((dsp, _))
            .recover { case _ ⇒ (dsp, null) }
        }

        deletedFile.foreach(_.foreach { case (dispatcher, _) ⇒ dispatcher ! PoisonPill })

        sc.ops.region.deleteFiles(regionId, regionPath)
          .map(FileVersions.mostRecent)
          .recoverWith { case _ if deletedFile.nonEmpty ⇒
            deletedFile.get
              .map(_._2)
              .filter(_ ne null)
          }
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

    def renameFile(path: Path, newPath: Path): Future[File] = {
      if (path.isRoot || path.isRegionRoot || newPath.isRoot || newPath.isRegionRoot)
        return Future.failed(StorageException.NotFound(path))

      val (regionId, oldRegionPath) = path.regionAndPath
      val (regionId2, newRegionPath) = newPath.regionAndPath

      if (regionId != regionId2)
        return Future.failed(StorageException.IOFailure(path, new IOException("Regions id should match")))

      // Close old file
      closeFile(newPath)

      if (state.fileWrites.contains(path)) {
        for {
          oldFile ← syncFile(path)
          newFile ← sc.ops.region.createFile(regionId, oldFile.copy(path = newRegionPath))
          _ ← DeleteFile.unwrapFuture(self ? DeleteFile(path))
        } yield newFile
      } else {
        for {
          oldFiles ← sc.ops.region.getFiles(regionId, oldRegionPath)
          oldFile = FileVersions.mostRecent(oldFiles)
          newFile ← sc.ops.region.createFile(regionId, oldFile.copy(path = newRegionPath))
          _ ← DeleteFile.unwrapFuture(self ? DeleteFile(path))
        } yield newFile
      }
    }

    def renameFolder(path: Path, newPath: Path): Future[Folder] = {
      if (path.isRoot || path.isRegionRoot || newPath.isRoot || newPath.isRegionRoot)
        return Future.failed(StorageException.NotFound(path))

      val (regionId, oldRegionPath) = path.regionAndPath
      val (regionId2, newRegionPath) = newPath.regionAndPath

      if (regionId != regionId2)
        return Future.failed(StorageException.IOFailure(path, new IOException("Regions id should match")))

      for {
        oldFolder ← sc.ops.region.getFolder(regionId, oldRegionPath)
        index ← sc.ops.region.getFolderIndex(regionId)
        _ ← sc.ops.region.writeIndex(regionId, FolderIndexDiff.copyFolder(index, oldRegionPath, newRegionPath))
        newFolder ← sc.ops.region.getFolder(regionId, newRegionPath)
        _ ← sc.ops.region.deleteFolder(regionId, oldRegionPath)
      } yield newFolder
    }

    def closeFile(path: Path): Unit = {
      state.fileWrites
        .remove(path)
        .foreach(_ ! PoisonPill)
    }

    def closeFile(path: Path, dispatcher: ActorRef): Unit = {
      if (state.fileWrites.get(path).contains(dispatcher))
        state.fileWrites -= path
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
      OpenFile.wrapFuture(path, operations.getCurrentRevision(path))
        .pipeTo(sender())

    case CloseFile(path, dispatcher) ⇒
      operations.closeFile(path, dispatcher)

    case RenameFile(path, newPath) ⇒
      RenameFile.wrapFuture(path, operations.renameFile(path, newPath)).pipeTo(sender())

    case RenameFolder(path, newPath) ⇒
      RenameFolder.wrapFuture(path, operations.renameFolder(path, newPath)).pipeTo(sender())

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
          val storages = ss.storages
            .filter(_._2.actorState.isInstanceOf[ActorState.Active])
            .keys
            .toSeq

          val futures = storages.map { storageId ⇒
            sc.ops.storage.getHealth(storageId)
              .recover { case _ ⇒ StorageHealth.empty }
          }
          
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
