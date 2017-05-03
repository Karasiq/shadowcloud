package com.karasiq.shadowcloud.index

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasPath, HasWithoutData}
import com.karasiq.shadowcloud.index.File.Revision
import com.karasiq.shadowcloud.utils.Utils

object File {
  case class Revision(revision: Long) extends AnyVal with Comparable[Revision] {
    def next: Revision = {
      copy(revision + 1)
    }

    def compareTo(o: Revision): Int = {
      (revision - o.revision).toInt
    }
  }

  object Revision {
    val first = Revision(0)
  }
}

case class File(path: Path, timestamp: Timestamp = Timestamp.now, revision: Revision = Revision.first,
                checksum: Checksum = Checksum.empty, chunks: Seq[Chunk] = Nil)
  extends HasPath with HasEmpty with HasWithoutData {

  type Repr = File
  require(!path.isRoot)

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
      f.path == path && f.revision == revision && f.checksum == checksum && f.chunks == chunks
  }

  override def toString: String = {
    s"File($path#${revision.revision}, $checksum, chunks: [${Utils.printChunkHashes(chunks)}])"
  }
}
