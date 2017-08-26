package com.karasiq.shadowcloud.streams.chunk

import akka.util.ByteString

import com.karasiq.shadowcloud.index.Chunk

object ChunkRanges {
  case class Range(start: Long, end: Long) {
    require(start <= end, s"Invalid range: $start - $end")

    def length: Long = {
      end - start
    }

    def contains(range: Range): Boolean = {
      range.start < end && range.end > start
    }

    def relativeTo(fullRange: Range): Range = {
      copy(start - fullRange.start, end - fullRange.start)
    }

    def fitTo(fullRange: Range): Range = {
      copy(math.max(0L, start), math.min(fullRange.end, end))
    }

    def slice(data: ByteString): ByteString = {
      val start = math.max(0L, this.start)
      data.drop(start.toInt).take((end - start).toInt)
    }
  }

  def fromChunkStream(ranges: Seq[Range], chunkStream: Seq[Chunk]): Seq[(Chunk, Range)] = {
    val rangedChunks = {
      var position = 0L

      val rangedChunks = List.newBuilder[(Chunk, Range)]
      rangedChunks.sizeHint(chunkStream.length)

      for (chunk ← chunkStream) {
        val chunkSize = chunk.checksum.size
        val range = Range(position, position + chunkSize)
        rangedChunks += (chunk → range)
        position += chunkSize
      }
      rangedChunks.result()
    }

    ranges.flatMap { range ⇒
      for ((chunk, fullRange) ← rangedChunks if fullRange.contains(range))
        yield (chunk, range.relativeTo(fullRange).fitTo(fullRange))
    }
  }

  def slice(bytes: ByteString, ranges: Seq[Range]): ByteString = {
    ranges.map(_.slice(bytes)).fold(ByteString.empty)(_ ++ _)
  }

  def length(ranges: Seq[Range]): Long = {
    ranges.map(_.length).sum
  }
}
