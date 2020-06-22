package com.karasiq.shadowcloud.drive.fuse

import java.nio.file.Paths

import akka.Done
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.common.configs.ConfigUtils
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.drive.FileIOScheduler
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.exceptions.SCException
import com.karasiq.shadowcloud.model.{File, Folder, Path}
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.typesafe.config.Config
import jnr.ffi.Platform.OS
import jnr.ffi.{Platform, Pointer}
import ru.serce.jnrfuse.struct.{FileStat, FuseFileInfo, Statvfs}
import ru.serce.jnrfuse.{ErrorCodes, FuseFillDir, FuseStubFS}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object SCFileSystem {
  def apply(config: SCDriveConfig, fsDispatcher: ActorRef, log: LoggingAdapter)(implicit ec: ExecutionContext): SCFileSystem = {
    new SCFileSystem(config, fsDispatcher, log)
  }

  def getMountPath(config: Config = ConfigUtils.emptyConfig): String = {
    Try(config.getString("mount-path")).getOrElse {
      Platform.getNativePlatform.getOS match {
        case OS.WINDOWS ⇒ "S:\\"
        case OS.DARWIN  ⇒ "/Volumes/shadowcloud"
        case _          ⇒ "/mnt/sc"
      }
    }
  }

  def mountInSeparateThread(fs: SCFileSystem): Future[Done] = {
    val promise = Promise[Done]
    val thread = new Thread(() ⇒ {
      try {
        fs.mount()
        promise.success(Done)
      } catch {
        case NonFatal(exc) ⇒
          promise.failure(exc)
      }
    })
    thread.start()
    promise.future
  }

  private implicit def implicitStrToPath(path: String): Path = {
    def normalizePath(path: Path): Path = path.nodes match {
      case nodes :+ "."  ⇒ normalizePath(Path(nodes))
      case nodes :+ ""   ⇒ normalizePath(Path(nodes))
      case nodes :+ ".." ⇒ normalizePath(Path(nodes.dropRight(1)))
      case nodes :+ last ⇒ normalizePath(Path(nodes)) / last
      case Nil           ⇒ Path.root
    }
    normalizePath(Path(path.split(":?[/\\\\]+")))
  }
}

class SCFileSystem(config: SCDriveConfig, fsDispatcher: ActorRef, log: LoggingAdapter)(implicit ec: ExecutionContext) extends FuseStubFS {
  protected final case class FileHandle(handle: Long, file: File)

  import SCFileSystem.implicitStrToPath
  import com.karasiq.shadowcloud.drive.VirtualFSDispatcher._

  protected implicit val timeout = Timeout(config.fileIO.readTimeout)
  protected val writeTimeout     = Timeout(config.fileIO.writeTimeout)

  protected object settings {
    val fuseConfig             = config.rootConfig.getConfigIfExists("fuse")
    val mountPath              = SCFileSystem.getMountPath(fuseConfig)
    val debug                  = fuseConfig.withDefault(false, _.getBoolean("debug"))
    val options                = fuseConfig.withDefault(Nil, _.getStrings("options"))
    val synchronizedMode       = fuseConfig.withDefault(true, _.getBoolean("synchronized"))
    val persistRevisionOnFSync = fuseConfig.withDefault(false, _.getBoolean("persist-revision-on-fsync"))
    val flushOnFSync           = fuseConfig.withDefault(false, _.getBoolean("flush-on-fsync"))
  }

  protected val fileHandles = TrieMap.empty[Long, FileHandle]

  def mount(blocking: Boolean = false): Unit = {
    import settings._
    if (Platform.getNativePlatform.getOS != OS.WINDOWS) {
      import java.nio.file.Files
      Try(Files.createDirectory(Paths.get(mountPath)))
    }
    mount(Paths.get(mountPath), blocking, debug, Array("-o", options.mkString(",")))
  }

  protected def dispatch[T](message: AnyRef, status: MessageStatus[_, T], critical: Boolean = false, handle: Long = 0)(
      implicit timeout: Timeout
  ): T = {
    // if (critical) log.info(s"IO operation requested: $message")

    def getResult() = Await.result(status.unwrapFuture(fsDispatcher ? message), timeout.duration)
    val start       = System.nanoTime()
    val result = try {
      if (critical && settings.synchronizedMode) {
        val fh = if (handle == 0) null else fileHandles.get(handle).orNull
        if (fh eq null) synchronized(getResult())
        else fh.synchronized(getResult())
      } else {
        getResult()
      }
    } catch {
      case NonFatal(error) ⇒
        if (critical || settings.debug) log.error(error, "IO operation failed: {}", message)
        throw error
    }
    val elapsed = (System.nanoTime() - start).nanos
    if (settings.debug || elapsed >= (5 seconds)) log.info("IO operation completed in {} ms: {} -> {}", elapsed.toMillis, message, result)
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

    lazy val folder = Try(dispatch(GetFolder(path), GetFolder))
    lazy val file   = Try(dispatch(GetFile(path), GetFile))

    if (folder.isSuccess) {
      returnFolderAttrs(folder.get)
      0
    } else if (file.isSuccess) {
      returnFileAttrs(file.get)
      0
    } else -ErrorCodes.ENOENT()
  }

  override def mkdir(path: String, mode: Long): Int = {
    if (isTrashFile(path)) {
      log.warning("Trash folder not created: {}", path)
      return -ErrorCodes.EACCES()
    }

    Try(dispatch(CreateFolder(path), CreateFolder, critical = true)) match {
      case Success(_)                                       ⇒ 0
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_)                                       ⇒ -ErrorCodes.EIO()
    }
  }

  override def unlink(path: String): Int = {
    Try(dispatch(DeleteFile(path), DeleteFile, critical = true)) match {
      case Success(_)                                  ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_)                                  ⇒ -ErrorCodes.EIO()
    }
  }

  override def rmdir(path: String): Int = {
    Try(dispatch(DeleteFolder(path), DeleteFolder, critical = true)) match {
      case Success(_)                                  ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case Failure(_)                                  ⇒ -ErrorCodes.EIO()
    }
  }

  override def rename(oldpath: String, newpath: String): Int = {
    val file = Try(dispatch(RenameFile(oldpath, newpath), RenameFile, critical = true))
      .orElse(Try(dispatch(RenameFolder(oldpath, newpath), RenameFolder, critical = true)))

    file match {
      case Success(_)                                       ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc)      ⇒ -ErrorCodes.ENOENT()
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_)                                       ⇒ -ErrorCodes.EIO()
    }
  }

  override def truncate(path: String, size: Long): Int = {
    Try(dispatch(DispatchIOOperation(path, FileIOScheduler.CutFile(size)), DispatchIOOperation, critical = true)(writeTimeout)) match {
      case Success(_)                                       ⇒ 0
      case Failure(exc) if SCException.isNotFound(exc)      ⇒ -ErrorCodes.ENOENT()
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(_)                                       ⇒ -ErrorCodes.EIO()
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
      case Failure(_)                                  ⇒ -ErrorCodes.EIO()
    }
  }

  override def read(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    def tryRead() = {
      Try(
        dispatch(
          DispatchIOOperation(path, FileIOScheduler.ReadData(ChunkRanges.Range(offset, offset + size))),
          DispatchIOOperation,
          critical = true,
          handle = fi.fh.longValue()
        )
      )
    }

    var result: Try[Any] = tryRead()
    var tries            = 3
    while (result.isFailure && tries > 0) {
      // Thread.sleep(5000)
      result = tryRead()
      tries -= 1
    }

    result match {
      case Success(FileIOScheduler.ReadData.Success(_, data)) ⇒
        for (i ← data.indices) buf.putByte(i, data(i))
        data.length

      case Failure(exc) if SCException.isNotFound(exc) ⇒ -ErrorCodes.ENOENT()
      case _                                           ⇒ -ErrorCodes.EIO()
    }
  }

  override def write(path: String, buf: Pointer, size: Long, offset: Long, fi: FuseFileInfo): Int = {
    val bytes = {
      val array = new Array[Byte](size.toInt)
      buf.get(0, array, 0, size.toInt)
      ByteString.fromArrayUnsafe(array)
    }

    def tryWrite() =
      Try(
        dispatch(
          DispatchIOOperation(path, FileIOScheduler.WriteData(offset, bytes)),
          DispatchIOOperation,
          critical = true,
          handle = fi.fh.longValue()
        )(writeTimeout)
      )

    var result: Try[Any] = tryWrite()
    var tries            = 3
    while (result.isFailure && tries > 0) {
      // Thread.sleep(5000)
      result = tryWrite()
      tries -= 1
    }

    result match {
      case Success(FileIOScheduler.WriteData.Success(data, _)) ⇒ data.data.length
      case Failure(exc) if SCException.isNotFound(exc)         ⇒ -ErrorCodes.ENOENT()
      case _                                                   ⇒ -ErrorCodes.EIO()
    }
  }

  override def statfs(path: String, stbuf: Statvfs): Int = {
    Try(dispatch(GetHealth(path), GetHealth)) match {
      case Success(health) ⇒
        // MacOS limits this to 4.5 PB
        stbuf.f_bsize.set(config.blockSize)                      // fs block size
        stbuf.f_frsize.set(config.blockSize)                     // fs block size
        stbuf.f_blocks.set(health.totalSpace / config.blockSize) // total data blocks in file system
        stbuf.f_bfree.set(health.freeSpace / config.blockSize)   // free blocks in fs
        stbuf.f_bavail.set(health.freeSpace / config.blockSize)  // free blocks in fs
        stbuf.f_ffree.set(Int.MaxValue)
        stbuf.f_favail.set(Int.MaxValue)
        0

      case Failure(_) ⇒
        stbuf.f_blocks.set(0) // total data blocks in file system
        stbuf.f_bfree.set(0)  // free blocks in fs
        stbuf.f_bavail.set(0) // free blocks in fs
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
      case Failure(_)                                  ⇒ -ErrorCodes.EIO()
    }
  }

  override def fsync(path: String, isdatasync: Int, fi: FuseFileInfo): Int = {
    if (!settings.flushOnFSync) return 0

    val flushResult =
      Try(dispatch(DispatchIOOperation(path, FileIOScheduler.Flush), DispatchIOOperation, critical = true, handle = fi.fh.longValue())(writeTimeout))

    if (settings.persistRevisionOnFSync) {
      val persistResult = Try {
        val Success(FileIOScheduler.Flush.Success(_, _)) = flushResult
        dispatch(DispatchIOOperation(path, FileIOScheduler.PersistRevision), DispatchIOOperation, critical = true, handle = fi.fh.longValue())
      }

      persistResult match {
        case Success(FileIOScheduler.PersistRevision.Success(_, _)) ⇒ 0
        case Failure(exc) if SCException.isNotFound(exc)            ⇒ -ErrorCodes.ENOENT()
        case _                                                      ⇒ -ErrorCodes.EIO()
      }
    } else {
      flushResult match {
        case Success(FileIOScheduler.Flush.Success(_, _)) ⇒ 0
        case Failure(exc) if SCException.isNotFound(exc)  ⇒ -ErrorCodes.ENOENT()
        case _                                            ⇒ -ErrorCodes.EIO()
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
      case _                                           ⇒ -ErrorCodes.EIO()
    }
  }

  override def create(path: String, mode: Long, fi: FuseFileInfo): Int = {
    if (isTrashFile(path)) {
      log.warning("Trash file not created: {}", path)
      return -ErrorCodes.EACCES()
    }

    Try(dispatch(CreateFile(path), CreateFile, critical = true)) match {
      case Success(_)                                       ⇒ 0
      case Failure(exc) if SCException.isAlreadyExists(exc) ⇒ -ErrorCodes.EEXIST()
      case Failure(exc) if SCException.isNotFound(exc)      ⇒ -ErrorCodes.ENOENT()
      case _                                                ⇒ -ErrorCodes.EIO()
    }
  }

  private[this] def isTrashFile(path: Path): Boolean = {
    if (path.isRoot) return false

    Platform.getNativePlatform.getOS match {
      case OS.DARWIN  ⇒ path.name.startsWith("._") || path.name == ".DS_Store"
      case OS.WINDOWS ⇒ path.name == "$Recycle.bin"
      case _          ⇒ false
    }
  }
}
