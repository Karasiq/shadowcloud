package com.karasiq.shadowcloud.index

import java.util.UUID

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasPath, HasWithoutChunks, HasWithoutData}
import com.karasiq.shadowcloud.index.File.ID
import com.karasiq.shadowcloud.utils.Utils

case class File(path: Path, id: ID = File.newFileId,
                revision: Long = 0, timestamp: Timestamp = Timestamp.now,
                props: SerializedProps = SerializedProps.empty,
                checksum: Checksum = Checksum.empty, chunks: Seq[Chunk] = Nil)
  extends HasPath with HasEmpty with HasWithoutData with HasWithoutChunks {

  type Repr = File
  require(!path.isRoot)

  def withoutData: File = {
    copy(chunks = chunks.map(_.withoutData))
  }

  def withoutChunks: File = {
    copy(chunks = Seq.empty)
  }

  def isEmpty: Boolean = {
    chunks.isEmpty
  }

  override def hashCode(): Int = {
    (path, id, revision, checksum /*, chunks */).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: File ⇒
      // Timestamp, props is ignored
      @inline def chunksEqual: Boolean = (f.chunks.isEmpty || chunks.isEmpty) || (f.chunks == chunks)
      f.path == path && f.id == id && f.revision == revision && f.checksum == checksum && chunksEqual
  }

  override def toString: String = {
    s"File($path#$revision, $checksum, chunks: [${Utils.printChunkHashes(chunks, 5)}])"
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
    file.copy(id = File.newFileId, revision = file.revision + 1, timestamp = file.timestamp.modifiedNow, checksum = newChecksum, chunks = newChunks)
  }

  def isBinaryEquals(f1: File, f2: File): Boolean = {
    f1.checksum == f2.checksum && f1.chunks == f2.chunks
  }
}
