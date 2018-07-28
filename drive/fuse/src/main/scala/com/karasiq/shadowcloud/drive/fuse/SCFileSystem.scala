package com.karasiq.shadowcloud.drive.fuse

import java.nio.file.Paths

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal

import akka.Done
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.typesafe.config.Config
import jnr.ffi.{Platform, Pointer}
import jnr.ffi.Platform.OS
import ru.serce.jnrfuse.{ErrorCodes, FuseFillDir, FuseStubFS}
import ru.serce.jnrfuse.struct.{FileStat, FuseFileInfo, Statvfs}

import com.karasiq.common.configs.ConfigUtils
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.drive.FileIOScheduler
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.exceptions.SCException
import com.karasiq.shadowcloud.model.{File, Folder, Path}
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

object SCFileSystem {
  def apply(config: SCDriveConfig, fsDispatcher: ActorRef, log: LoggingAdapter)(implicit ec: ExecutionContext): SCFileSystem = {
    new SCFileSystem(config, fsDispatcher, log)
  }

  def getMountPath(config: Config = ConfigUtils.emptyConfig): String = {
    Try(config.getString("mount-path")).getOrElse {
      Platform.getNativePlatform.getOS match {
        case OS.WINDOWS ⇒ "S:\\"
        case _ ⇒ "/mnt/sc"
      }
    }
  }

  def mountInSeparateThread(fs: SCFileSystem): Future[Done] = {
    val promise = Promise[Done]
    val thread = new Thread(new Runnable {
      def run(): Unit = {
        try {
          fs.mount()
          promise.success(Done)
        } catch { case NonFatal(exc) ⇒
          promise.failure(exc)
        }
      }
    })
    thread.start()
    promise.future
  }

  private implicit def implicitStrToPath(path: String): Path = {
    def normalizePath(path: Path): Path = path.nodes match {
      case nodes :+ "." ⇒ normalizePath(Path(nodes))
      case nodes :+ "" ⇒ normalizePath(Path(nodes))
      case nodes :+ ".." ⇒ normalizePath(Path(nodes.dropRight(1)))
      case nodes :+ last ⇒ normalizePath(Path(nodes)) / last
      case Nil ⇒ Path.root 
    }
    normalizePath(Path(path.split(":?[/\\\\]+")))
  }
}

class SCFileSystem(config: SCDriveConfig, fsDispatcher: ActorRef, log: LoggingAdapter)(implicit ec: ExecutionContext) extends FuseStubFS {
  protected final case class FileHandle(handle: Long, file: File)

  import SCFileSystem.implicitStrToPath
  import com.karasiq.shadowcloud.drive.VirtualFSDispatcher._

  protected implicit val timeout = Timeout(config.fileIO.timeout)

  protected object settings {
    val fuseConfig = config.rootConfig.getConfigIfExists("fuse")
    val mountPath = SCFileSystem.getMountPath(fuseConfig)
    val debug = fuseConfig.withDefault(false, _.getBoolean("debug"))
    val options = fuseConfig.withDefault(Nil, _.getStrings("options"))
    val synchronizedMode = fuseConfig.withDefault(true, _.getBoolean("synchronized"))
    val persistRevisionOnFSync = fuseConfig.withDefault(false, _.getBoolean("persist-revision-on-fsync"))
  }

  protected val fileHandles = TrieMap.empty[Long, FileHandle]

  def mount(blocking: Boolean = false): Unit = {
    import settings._
    mount(Paths.get(mountPath), blocking, debug, options.toArray)
  }

  protected def dispatch[T](message: AnyRef, status: MessageStatus[_, T], critical: Boolean = false, handle: Long = 0): T = {
    def getResult() = Await.result(status.unwrapFuture(fsDispatcher ? message), timeout.duration)
    val result = try {
      if (critical && settings.synchronizedMode) {
        val fh = if (handle == 0) null else fileHandles.get(handle).orNull
        if (fh eq null) synchronized(getResult())
        else fh.synchronized(getResult())
      } else {
        getResult()
      }
    } catch { case NonFatal(error) ⇒
      if (critical || log.isDebugEnabled) log.error(error, "IO operation failed: {}", message)
      throw error
    }
    if (critical) log.debug("IO operation: {} -> {}", message, result)
    result
  }


  override def getattr(path: String, stat: FileStat): Int = {
    def returnFolderAttrs(folder: Folder): Unit = {
      stat.st_mode.set(FileStat.S_IFDIR | 0x1ff)
      // stat.st_nlink.set(1)
      stat.st_uid.set(getContext.uid.get)
      stat.st_gid.set(getContext.pid.get)
      stat.st_birthtime.tv_sec.set(folder.timestamp.created / 1000)
      stat.st_birthtime.tv_nsec.set((folder.timestamp.created % 1000) * 1000)
      stat.st_mtim.tv_sec.set(folder.timestamp.lastModified / 1000)
      stat.st_mtim.tv_nsec.set((folder.timestamp.lastModified % 1000) * 1000)
      stat.st_atim.tv_sec.set(folder.timestamp.lastModified / 1000)
      stat.st_atim.tv_nsec.set((folder.timestamp.lastModified % 1000) * 1000)
    }

    def returnFileAttrs(file: File): Unit = {
      stat.st_mode.set(FileStat.S_IFREG | 0x1ff)
      // stat.st_nlink.set(1)
      stat.st_uid.set(getContext.uid.get)
      stat.st_gid.set(getContext.pid.get)
      stat.st_size.set(file.checksum.size)
      stat.st_ino.set(file.id.getMostSignificantBits)
      stat.st_birthtime.tv_sec.set(file.timestamp.created / 1000)
      stat.st_birthtime.tv_nsec.set((file.timestamp.created % 1000) * 1000)
      stat.st_mtim.tv_sec.set(file.timestamp.lastModified / 1000)
      stat.st_mtim.tv_nsec.set((file.timestamp.lastModified % 1000) * 1000)
      stat.st_atim.tv_sec.set(file.timestamp.lastModified / 1000)
      stat.st_atim.tv_nsec.set((file.timestamp.lastModified % 1000) * 1000)
    }

    val folder = Try(dispatch(GetFolder(path), GetFolder))
    lazy val file = Try(dispatch(GetFile(path), GetFile))
    if (folder.isSuccess) {
      returnFolderAttrs(folder.get)
      0
    } else if (file.isSuccess) {
      returnFileAttrs(file.get)
      0
    } else {
      -ErrorCodes.ENOENT()
    }
  }

  override def mkdir(path: String, mode: Long): Int = {
    Try(dispatch(CreateFolder(path), CreateFolder, critical = true)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_) ⇒ -ErrorCodes.ENOENT()
    }
  }

  override def unlink(path: String): Int = {
    Try(dispatch(DeleteFile(path), DeleteFile, critical = true)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def rmdir(path: String): Int = {
    Try(dispatch(DeleteFolder(path), DeleteFolder, critical = true)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def rename(oldpath: String, newpath: String): Int = {
    val file = Try(dispatch(RenameFile(oldpath, newpath), RenameFile, critical = true))
      .orElse(Try(dispatch(RenameFolder(oldpath, newpath), RenameFolder, critical = true)))

    file match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def truncate(path: String, size: Long): Int = {
    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.CutFile(size)), DispatchIOOperation, critical = true)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def open(path: String, fi: FuseFileInfo): Int = {
    Try(dispatch(GetFile(path), GetFile, critical = true)) match {
      case Success(file) ⇒
        val handle = file.id.getMostSignificantBits
        fileHandles(handle) = FileHandle(handle, file)
        fi.fh.set(handle)
        0

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def read(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    def tryRead() = {
      Try(dispatch(DispatchIOOperation(path, FileIOScheduler.ReadData(ChunkRanges.Range(offset, offset + size))), DispatchIOOperation, critical = true, handle = fi.fh.longValue()))
    }

    var result: Try[Any] = tryRead()
    var tries = 3
    while (result.isFailure && tries > 0) {
      Thread.sleep(5000)
      result = tryRead()
      tries -= 1
    }

    result match {
      case Success(FileIOScheduler.ReadData.Success(_, data)) ⇒
        for (i ← data.indices) buf.putByte(i, data(i))
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

    def tryWrite() = Try(dispatch(DispatchIOOperation(path, FileIOScheduler.WriteData(offset, bytes)), DispatchIOOperation, critical = true, handle = fi.fh.longValue()))

    var result: Try[Any] = tryWrite()
    var tries = 3
    while (result.isFailure && tries > 0) {
      Thread.sleep(5000)
      result = tryWrite()
      tries -= 1
    }

    result match {
      case Success(FileIOScheduler.WriteData.Success(data, _)) ⇒ data.data.length
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }

  override def statfs(path: String, stbuf: Statvfs): Int = {
    Try(dispatch(GetHealth(path), GetHealth)) match {
      case Success(health) ⇒
        stbuf.f_frsize.set(512) // fs block size
        stbuf.f_blocks.set(health.totalSpace / 512) // total data blocks in file system
        stbuf.f_bfree.set(health.writableSpace / 512) // free blocks in fs
        0

      case Failure(_) ⇒
        stbuf.f_frsize.set(512) // fs block size
        stbuf.f_blocks.set(0) // total data blocks in file system
        stbuf.f_bfree.set(0) // free blocks in fs
        0

      // case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      // case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def release(path: String, fi: FuseFileInfo): Int = {
    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.ReleaseFile), DispatchIOOperation, critical = true, handle = fi.fh.longValue())) match {
      case Success(_) ⇒
        fileHandles -= fi.fh.longValue()
        0

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_) ⇒ -ErrorCodes.EIO()
    }
  }

  override def fsync(path: String, isdatasync: Int, fi: FuseFileInfo): Int = {
    val flushResult = Try(dispatch(DispatchIOOperation(path, FileIOScheduler.Flush), DispatchIOOperation, critical = true, handle = fi.fh.longValue()))

    if (settings.persistRevisionOnFSync) {
      val persistResult = Try {
        val Success(FileIOScheduler.Flush.Success(_, _)) = flushResult
        dispatch(DispatchIOOperation(path, FileIOScheduler.PersistRevision), DispatchIOOperation, critical = true, handle = fi.fh.longValue())
      }

      persistResult match {
        case Success(FileIOScheduler.PersistRevision.Success(_, _)) ⇒ 0
        case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
        case _ ⇒ -ErrorCodes.EIO()
      }
    } else {
      flushResult match {
        case Success(FileIOScheduler.Flush.Success(_, _)) ⇒ 0
        case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
        case _ ⇒ -ErrorCodes.EIO()
      }
    }
  }

  override def readdir(path: String, buf: Pointer, filter: FuseFillDir, offset: Long, fi: FuseFileInfo): Int = {
    Try(dispatch(GetFolder(path), GetFolder)) match {
      case Success(folder) ⇒
        val names = folder.folders ++ folder.files.map(_.path.name)
        filter.apply(buf, ".", null, 0)
        filter.apply(buf, "..", null, 0)
        names
          .filter(str ⇒ Path.isStrictlyConventional(Path(Seq(str))))
          .foreach(filter.apply(buf, _, null, 0))
        0

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }

  override def create(path: String, mode: Long, fi: FuseFileInfo): Int = {
    Try(dispatch(CreateFile(path), CreateFile, critical = true)) match {
      case Success(_) ⇒ 0
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _ ⇒ -ErrorCodes.EIO()
    }
  }
}
