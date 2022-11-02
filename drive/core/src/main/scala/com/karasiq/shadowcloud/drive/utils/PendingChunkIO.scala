package com.karasiq.shadowcloud.drive.utils

import akka.util.ByteString

import com.karasiq.shadowcloud.model.Chunk
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges

private[drive] sealed trait PendingChunkIO {
  def range: ChunkRanges.Range
}

private[drive] object PendingChunkIO {
  final case class Rewrite(range: ChunkRanges.Range, chunk: Chunk, patches: ChunkPatchList) extends PendingChunkIO
  final case class Append(range: ChunkRanges.Range, newData: ByteString)                    extends PendingChunkIO
}
