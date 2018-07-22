package com.karasiq.shadowcloud.drive.utils

import scala.annotation.tailrec
import scala.language.implicitConversions

import akka.util.ByteString

import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

private[drive] final case class ChunkPatch(offset: Long, data: ByteString) {
  val range = ChunkRanges.Range(offset, offset + data.length)

  override def toString: String = s"ChunkPatch($offset, ${data.length} bytes)"
}

private[drive] final case class ChunkPatchList(patches: Seq[ChunkPatch]) {
  def canReplace(chunkSize: Long): Boolean = {
    @tailrec
    def canReplaceRec(position: Long, patches: Seq[ChunkPatch]): Boolean = patches match {
      case Nil ⇒ position >= chunkSize
      case p +: rest if p.offset == position ⇒ canReplaceRec(position + p.data.length, rest)
      case _ ⇒ false
    }

    canReplaceRec(0, this.patches.sortBy(_.offset))
  }

  def toBytes(chunkSize: Int): ByteString = {
    val zeroBytes = ByteString(new Array[Byte](chunkSize))
    patchChunk(0 until chunkSize, zeroBytes)
  }

  def patchChunk(dataRange: ChunkRanges.Range, data: ByteString) = {
    patches.foldLeft(data) { case (data, write) ⇒
      val relRange = write.range.relativeTo(dataRange)
      val offset = dataRange.relativeTo(write.range)
      ChunkRanges.Range.patch(data, relRange, offset.slice(write.data))
    }
  }
}

private[drive] object ChunkPatchList {
  implicit def fromPatchesSeq(patches: Seq[ChunkPatch]): ChunkPatchList = ChunkPatchList(patches)
  implicit def toPatchesSeq(pl: ChunkPatchList): Seq[ChunkPatch] = pl.patches
}