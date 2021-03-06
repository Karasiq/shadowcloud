package com.karasiq.shadowcloud.shell

import akka.stream.IOResult
import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.model.{File, Folder}

import scala.collection.GenTraversableOnce
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

private[shell] object ShellUtils {
  def toString(f: File): String = {
    s"${f.path.name} [${Integer.toHexString(f.hashCode())}] (${MemorySize(f.checksum.size)})"
  }

  def toStrings(folder: Folder): Seq[String] = {
    Seq("Folders:") ++ folder.folders.toSeq.sorted.map("  - " + _) ++
    Seq("Files:") ++ folder.files.toSeq.sortBy(f ⇒ (f.path.name, f.timestamp.lastModified)).map(f ⇒ s"  - ${ShellUtils.toString(f)}")
  }

  def print[T](future: Future[T])(toStrings: T ⇒ GenTraversableOnce[String])(implicit ec: ExecutionContext): Unit = {
    future.onComplete {
      case Success(value) ⇒
        toStrings(value).foreach(println)

      case Failure(error) ⇒
        error.printStackTrace()
    }
  }

  def printIOResult(future: Future[IOResult])(implicit ec: ExecutionContext): Unit = {
    print(future) { result ⇒
      if (result.status.isFailure) {
        Array(s"Failure: ${result.status.failed.get}")
      } else {
        Array(s"Success: ${MemorySize(result.count)} written")
      }
    }
  }
}
