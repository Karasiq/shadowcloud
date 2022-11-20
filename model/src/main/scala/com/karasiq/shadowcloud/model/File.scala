package com.karasiq.shadowcloud.model

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.index.utils._
import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class File(
    path: Path,
    id: FileId = FileId.create(),
    revision: Long = 0,
    timestamp: Timestamp = Timestamp.now,
    props: SerializedProps = SerializedProps.empty,
    checksum: Checksum = Checksum.empty,
    chunks: Seq[Chunk] = Nil
) extends SCEntity
    with HasPath
    with HasEmpty
    with HasWithoutData
    with HasWithoutChunks
    with HasWithoutKeys {

  type Repr = File
  require(!path.isRoot, "Root can not be a file")

  @transient
  private[this] lazy val _hashCode = (path, id /*, revision, checksum, chunks*/ ).hashCode()

  def withoutData: File = {
    copy(chunks = chunks.map(_.withoutData))
  }

  def withoutChunks: File = {
    copy(chunks = Seq.empty)
  }

  def withoutKeys = {
    copy(chunks = chunks.map(_.withoutKeys))
  }

  def isEmpty: Boolean = {
    chunks.isEmpty
  }

  override def hashCode(): Int = {
    _hashCode
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: File ⇒
      // @inline def isChunksEquals: Boolean = (f.chunks.isEmpty || chunks.isEmpty) || (f.chunks == chunks)
      f.path == path && f.id == id && f.revision == revision && f.checksum == checksum // && isChunksEquals

    case _ ⇒
      false
  }

  override def toString: String = {
    s"File($path#$revision, $checksum, chunks: [${Utils.printChunkHashes(chunks, 5)}])"
  }
}

object File {
  def create(path: Path, checksum: Checksum, chunks: Seq[Chunk]): File = {
    File(path, checksum = checksum, chunks = chunks)
  }

  def modified(file: File, newChecksum: Checksum, newChunks: Seq[Chunk]): File = {
    file.copy(id = FileId.create(), revision = file.revision + 1, timestamp = file.timestamp.modifiedNow, checksum = newChecksum, chunks = newChunks)
  }

  def isBinaryEquals(f1: File, f2: File): Boolean = {
    f1.checksum == f2.checksum && f1.chunks == f2.chunks
  }
}
