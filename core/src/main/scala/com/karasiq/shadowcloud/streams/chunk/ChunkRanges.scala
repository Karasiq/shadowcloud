package com.karasiq.shadowcloud.streams.chunk

import scala.annotation.tailrec

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

    def fitToSize(dataSize: Long): Range = {
      copy(math.max(0L, start), math.min(dataSize, end))
    }

    def slice(data: ByteString): ByteString = {
      val start = math.max(0L, this.start)
      data.drop(start.toInt).take((end - start).toInt)
    }
  }

  case class RangeList(ranges: Seq[Range]) {
    def slice(bytes: ByteString): ByteString = {
      ranges
        .map(_.slice(bytes))
        .fold(ByteString.empty)(_ ++ _)
    }

    def length: Long = {
      ranges.map(_.length).sum
    }

    def +(range: Range): RangeList = {
      copy(ranges :+ range)
    }
  }

  object RangeList {
    def apply(r1: Range, rs: Range*): RangeList = {
      new RangeList(r1 +: rs)
    }

    def mapChunkStream(ranges: RangeList, chunkStream: Seq[Chunk]): Seq[(Chunk, RangeList)] = {
      @tailrec
      def groupRanges(ranges: Seq[(Chunk, Range)], currentChunk: Option[(Chunk, RangeList)],
                      result: Seq[(Chunk, RangeList)]): Seq[(Chunk, RangeList)] = ranges match {
        case (chunk, range) +: tail ⇒
          if (currentChunk.exists(_._1 == chunk)) {
            groupRanges(tail, currentChunk.map { case (c, ranges) ⇒ (c, ranges + range) }, result)
          } else {
            groupRanges(tail, Some((chunk, RangeList(range))), result ++ currentChunk)
          }

        case Nil ⇒
          result ++ currentChunk
      }

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

      val flatRanged = ranges.ranges.flatMap { range ⇒
        for ((chunk, fullRange) ← rangedChunks if fullRange.contains(range))
          yield (chunk, range.relativeTo(fullRange).fitToSize(chunk.checksum.size))
      }

      groupRanges(flatRanged, None, Nil)
    }
  }
}
