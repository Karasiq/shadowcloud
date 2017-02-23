package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasPath, HasWithoutData}
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class File(path: Path, created: Long = 0, lastModified: Long = 0,
                checksum: Checksum = Checksum.empty, chunks: Seq[Chunk] = Nil)
  extends HasPath with HasEmpty with HasWithoutData {

  type Repr = File
  require(!path.isRoot && lastModified >= created)

  def withoutData: File = {
    copy(chunks = chunks.map(_.withoutData))
  }

  def isEmpty: Boolean = {
    chunks.isEmpty
  }

  override def hashCode(): Int = {
    (path, checksum, chunks).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: File ⇒
      f.path == path && f.checksum == checksum && f.chunks == chunks
  }

  override def toString: String = {
    s"File($path, $checksum, chunks: [${Utils.printHashes(chunks)}])"
  }
}
