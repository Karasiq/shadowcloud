package com.karasiq.shadowcloud.storage.rclone

import java.io.{BufferedInputStream, BufferedOutputStream, InputStream, OutputStream}
import java.nio.file.{Files, Paths}

import akka.NotUsed
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.{Try, Using}
import scala.collection.mutable

object RCloneRepository {
  def apply(remoteName: String, rclonePath: String = "rclone")(
    implicit ec: ExecutionContext
  ): RCloneRepository =
    new RCloneRepository(remoteName, rclonePath)

  /**
   * Parse RClone JSON output into structured data.
   */
  private[rclone] def parseJsonLines(output: String): Seq[Map[String, String]] = {
    import scala.util.parsing.json.JSON
    output.trim.split("\n")
      .filterNot(_.trim.isEmpty)
      .flatMap { line ⇒
        Try(JSON.parseFull(line))
          .toOption
          .flatMap { case m: Map[String, Any] @unchecked ⇒ Some(m.map { case (k, v) ⇒ k ⇒ v.toString }) }
      }
  }
}

/**
 * RClone storage repository enabling access to multiple cloud backends.
 *
 * Features:
 * - Support for all RClone remotes (S3, GCS, Azure Blob, Dropbox, etc.)
 * - Process-based RClone execution with streaming stdin/stdout
 * - Efficient file I/O without intermediate buffering
 * - Concurrent RClone operations with process pooling
 * - Automatic process lifecycle management
 * - Type-safe path handling and error reporting
 * - Streaming directory listings via JSON output
 * - Backpressure support via Akka Streams
 */
private[rclone] class RCloneRepository(
  remoteName: String,
  rclonePath: String
)(implicit ec: ExecutionContext)
    extends PathTreeRepository {

  private[this] val processPool = mutable.Queue[Process]()
  private[this] val maxPoolSize = Runtime.getRuntime.availableProcessors()

  /**
   * List all files and directories at a given path.
   *
   * Uses 'rclone lsf --json' for efficient directory listing.
   *
   * @param fromPath starting path
   * @return source of subpaths
   */
  def subKeys(fromPath: Path) = {
    Source
      .lazyFuture(() ⇒ listRCloneFiles(fromPath))
      .flatMapConcat(files ⇒ Source(files.toList))
      .map(fileName ⇒ fromPath / fileName)
      .via(StorageUtils.wrapStream(fromPath))
      .withAttributes(defaultAttributes)
      .named("rcloneSubKeys")
  }

  /**
   * Read file content as a stream.
   *
   * Uses 'rclone cat' for efficient streaming output.
   *
   * @param key file path
   * @return source of file data bytes
   */
  def read(key: Path) = {
    Source
      .lazyFuture(() ⇒ readRCloneFile(key))
      .mapMaterializedValue(fs ⇒ StorageUtils.wrapFuture(key, fs))
      .idleTimeout(30 seconds)
      .withAttributes(fileStreamAttributes)
      .named("rcloneRead")
  }

  /**
   * Write file content from a stream via RClone.
   *
   * Uses 'rclone rcat' for streaming input.
   *
   * @param path target file path
   * @return sink that accepts data bytes
   */
  def write(path: Path) = {
    Flow[Data]
      .via(AkkaStreamUtils.extractUpstream)
      .mapAsyncUnordered(1) { stream ⇒
        for {
          process ← acquireRCloneProcess("rcat", path)
        } yield (stream, process)
      }
      .flatMapConcat { case (stream, process) ⇒
        stream
          .via(
            Flow[ByteString]
              .mapAsyncUnordered(1) { data ⇒
                Future(blocking {
                  process.getOutputStream.write(data.toArray)
                  process.getOutputStream.flush()
                  data.length
                })(Dispatcher.IO)
              }
          )
          .map(written ⇒ StorageIOResult.Success(path, written.toLong))
          .watchTermination() { (mat, done) ⇒
            done.onComplete { _ ⇒
              Try {
                process.getOutputStream.close()
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                  val stderr = scala.io.Source.fromInputStream(process.getErrorStream)
                    .getLines()
                    .mkString("\n")
                  throw new IllegalStateException(s"RClone write failed: $stderr")
                }
                releaseRCloneProcess(process)
              }
            }
            mat
          }
      }
      .withAttributes(fileStreamAttributes)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.stoppingDecider))
      .via(StorageUtils.wrapStream(path))
      .toMat(Sink.head)(Keep.right)
      .named("rcloneWrite")
  }

  /**
   * Delete files at given paths.
   *
   * Uses 'rclone delete' for efficient deletion.
   *
   * @return sink that accepts paths to delete
   */
  def delete = {
    Flow[Path]
      .mapAsyncUnordered(2) { path ⇒
        for {
          _ ← deleteRCloneFile(path)
        } yield path
        
        StorageUtils.wrapCountFuture(path, Future.successful(1))
      }
      .idleTimeout(15 seconds)
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .withAttributes(defaultAttributes)
  }

  /**
   * List files in RClone remote using 'rclone lsf --json'.
   */
  private[this] def listRCloneFiles(path: Path): Future[Seq[String]] = Future(blocking {
    val rpath = encodeRClonePath(path)
    val process = new ProcessBuilder(
      rclonePath, "lsf", "--json", s"$remoteName:$rpath"
    ).redirectErrorStream(true).start()

    try {
      val output = scala.io.Source.fromInputStream(process.getInputStream)
        .getLines()
        .mkString("\n")
      
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw StorageException.NotFound(path)
      }

      // Parse JSON lines to extract file names
      RCloneRepository.parseJsonLines(output)
        .flatMap(_.get("Name"))
        .map(_.split('/').last)
        .distinct
    } finally {
      process.destroy()
    }
  })

  /**
   * Read file from RClone using 'rclone cat'.
   */
  private[this] def readRCloneFile(path: Path): Source[ByteString, Future[StorageIOResult]] = {
    Source
      .lazyFuture(() ⇒ Future(blocking {
        val rpath = encodeRClonePath(path)
        new ProcessBuilder(
          rclonePath, "cat", s"$remoteName:$rpath"
        ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
      }))
      .flatMapConcat { process ⇒
        StreamConverters.fromInputStream(() ⇒ process.getInputStream, 64 * 1024)
          .watchTermination() { (mat, done) ⇒
            done.onComplete { result ⇒
              Try {
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                  throw new IllegalStateException(s"RClone read failed with code: $exitCode")
                }
              }
            }
            mat
          }
      }
      .map(bs ⇒ StorageIOResult.Success(path, bs.length.toLong))
      .mapMaterializedValue(_.map(_ ⇒ StorageIOResult.Success(path, 0L)))
  }

  /**
   * Write file to RClone using 'rclone rcat'.
   */
  private[this] def writeRCloneFile(
    path: Path,
    inputStream: InputStream
  ): Future[Int] = Future(blocking {
    val rpath = encodeRClonePath(path)
    val process = new ProcessBuilder(
      rclonePath, "rcat", s"$remoteName:$rpath"
    ).redirectError(ProcessBuilder.Redirect.INHERIT).start()

    try {
      val output = process.getOutputStream
      val buffer = new Array[Byte](64 * 1024)
      var totalBytes = 0
      var bytesRead = 0

      while ( { bytesRead = inputStream.read(buffer); bytesRead } > 0) {
        output.write(buffer, 0, bytesRead)
        totalBytes += bytesRead
      }

      output.close()
      val exitCode = process.waitFor()
      
      if (exitCode != 0) {
        throw new IllegalStateException(s"RClone write failed with code: $exitCode")
      }

      totalBytes
    } finally {
      process.destroy()
    }
  })

  /**
   * Delete file from RClone using 'rclone delete'.
   */
  private[this] def deleteRCloneFile(path: Path): Future[Unit] = Future(blocking {
    val rpath = encodeRClonePath(path)
    val process = new ProcessBuilder(
      rclonePath, "delete", s"$remoteName:$rpath"
    ).redirectError(ProcessBuilder.Redirect.INHERIT).start()

    try {
      val exitCode = process.waitFor()
      if (exitCode != 0) {
        throw new IllegalStateException(s"RClone delete failed with code: $exitCode")
      }
    } finally {
      process.destroy()
    }
  })

  /**
   * Acquire RClone process from pool or create new one.
   */
  private[this] def acquireRCloneProcess(command: String, path: Path): Future[Process] = Future(blocking {
    synchronized {
      if (processPool.nonEmpty) {
        processPool.dequeue()
      } else {
        val rpath = encodeRClonePath(path)
        new ProcessBuilder(
          rclonePath, command, s"$remoteName:$rpath"
        ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
      }
    }
  })

  /**
   * Release RClone process back to pool.
   */
  private[this] def releaseRCloneProcess(process: Process): Unit = {
    synchronized {
      if (processPool.size < maxPoolSize && process.isAlive) {
        processPool.enqueue(process)
      } else {
        process.destroy()
      }
    }
  }

  /**
   * Encode path for RClone remote path format.
   */
  private[this] def encodeRClonePath(path: Path): String = {
    val encoded = path.nodes
      .map(node ⇒ java.net.URLEncoder.encode(node, "UTF-8"))
      .mkString("/")
    if (encoded.isEmpty) "/" else encoded
  }

  private[this] def defaultAttributes = {
    ActorAttributes.dispatcher("rclone-api-dispatcher")
  }

  private[this] def fileStreamAttributes = {
    ActorAttributes.dispatcher("rclone-file-dispatcher")
  }
}
