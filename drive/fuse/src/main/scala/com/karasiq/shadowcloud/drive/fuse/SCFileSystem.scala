package com.karasiq.shadowcloud.drive.fuse

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import jnr.ffi.Pointer
import ru.serce.jnrfuse.{ErrorCodes, FuseFillDir, FuseStubFS}
import ru.serce.jnrfuse.struct.{FuseFileInfo, Statvfs}

import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.drive.FileIOScheduler
import com.karasiq.shadowcloud.exceptions.SCException
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

class SCFileSystem(fsDispatcher: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) extends FuseStubFS {
  import com.karasiq.shadowcloud.drive.VirtualFSDispatcher._

  protected def dispatch[T](message: AnyRef, status: MessageStatus[_, T]): T = {
    Await.result(status.unwrapFuture(fsDispatcher ? message), Duration.Inf)
  }

  override def mkdir(path: String, mode: Long): Int = {
    Try(dispatch(CreateFolder(path), CreateFolder)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_) ⇒ -ErrorCodes.ENOENT()
    }
  }

  override def unlink(path: String): Int = {
    Try(dispatch(DeleteFile(path), DeleteFile)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def rmdir(path: String): Int = {
    Try(dispatch(DeleteFolder(path), DeleteFolder)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def rename(oldpath: String, newpath: String): Int = {
    Try(dispatch(RenameFile(oldpath, newpath), RenameFile)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def truncate(path: String, size: Long): Int = {
    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.CutFile(size)), DispatchIOOperation)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def open(path: String, fi: FuseFileInfo): Int = {
    Try(dispatch(GetFile(path), GetFile)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def read(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.ReadData(ChunkRanges.Range(offset, offset + size))), DispatchIOOperation)) match {
      case Success(FileIOScheduler.ReadData.Success(_, data)) ⇒
        buf.put(0, data.toArray, 0, data.length)
        data.length

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }

  override def write(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    val bytes = {
      val array = new Array[Byte](size.toInt)
      buf.get(0, array, 0, size.toInt)
      ByteString.fromArrayUnsafe(array)
    }

    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.WriteData(offset, bytes)), DispatchIOOperation)) match {
      case Success(FileIOScheduler.WriteData.Success(data, _)) ⇒
        data.data.length

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }

  override def statfs(path: String, stbuf: Statvfs): Int = {
    Try(dispatch(GetHealth(path), GetHealth)) match {
      case Success(health) ⇒
        stbuf.f_frsize.set(1024) // fs block size
        stbuf.f_blocks.set(health.totalSpace / 1024) // total data blocks in file system
        stbuf.f_bfree.set(health.writableSpace / 1024) // free blocks in fs
        0

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def release(path: String, fi: FuseFileInfo): Int = {
    Try(dispatch(ReleaseFile(path), ReleaseFile)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EACCES()
    }
  }

  override def fsync(path: String, isdatasync: Int, fi: FuseFileInfo): Int = {
    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.PersistRevision), DispatchIOOperation)) match {
      case Success(FileIOScheduler.PersistRevision.Success(_, _)) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }

  override def readdir(path: String, buf: Pointer, filter: FuseFillDir, offset: Long, fi: FuseFileInfo): Int = {
    Try(dispatch(GetFolder(path), GetFolder)) match {
      case Success(folder) ⇒
        val names = folder.folders ++ folder.files.map(_.path.name)
        names.foreach(filter.apply(buf, _, null, 0))
        0

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }

  override def create(path: String, mode: Long, fi: FuseFileInfo): Int = {
    Try(dispatch(CreateFile(path), CreateFile)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }
}
