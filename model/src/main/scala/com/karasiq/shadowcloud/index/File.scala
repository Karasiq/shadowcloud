package com.karasiq.shadowcloud.index

import java.util.UUID

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasPath, HasWithoutData}
import com.karasiq.shadowcloud.utils.Utils

case class File(path: Path, id: File.ID = File.newFileId, timestamp: Timestamp = Timestamp.now, revision: Long = 0,
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
    (path, id, revision, checksum /*, chunks */).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: File ⇒
      // Timestamp is ignored
      f.path == path && f.id == id && f.revision == revision && f.checksum == checksum && f.chunks == chunks
  }

  override def toString: String = {
    s"File($path#$revision, $checksum, chunks: [${Utils.printChunkHashes(chunks)}])"
  }
}

object File {
  type ID = java.util.UUID

  def newFileId: ID = { // TODO: Time based UUID
    UUID.randomUUID()
  }

  def create(path: Path, checksum: Checksum, chunks: Seq[Chunk]): File = {
    File(path, checksum = checksum, chunks = chunks)
  }

  def modified(file: File, newChecksum: Checksum, newChunks: Seq[Chunk]): File = {
    file.copy(
      id = File.newFileId,
      timestamp = file.timestamp.modifiedNow,
      revision = file.revision + 1,
      checksum = newChecksum,
      chunks = newChunks
    )
  }

  def isBinaryEquals(f1: File, f2: File): Boolean = {
    f1.checksum == f2.checksum && f1.chunks == f2.chunks
  }
}
