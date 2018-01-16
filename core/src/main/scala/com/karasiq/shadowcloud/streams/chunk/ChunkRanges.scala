package com.karasiq.shadowcloud.streams.chunk

import scala.annotation.tailrec
import scala.language.implicitConversions

import akka.util.ByteString

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.Chunk
import com.karasiq.shadowcloud.utils.Utils

object ChunkRanges {
  final case class Range(start: Long, end: Long) {
    require(start <= end, s"Invalid range: $start - $end")

    def size: Long = {
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
      if (start == 0 && end == data.length) {
        data
      } else {
        val start = math.max(0L, this.start)
        data.drop(start.toInt).take((end - start).toInt)
      }
    }
  }

  object Range {
    implicit def fromScalaRange(range: scala.Range): Range = {
      Range(range.start, if (range.isInclusive) range.end + 1 else range.end)
    }
    
    def patch(data: ByteString, range: Range, patchData: ByteString): ByteString = {
      val normalizedRange = range.fitToSize(data.length)
      require(normalizedRange.size <= patchData.length, "Invalid patch size")
      // data.take(normalizedRange.start.toInt) ++ patchData.take(normalizedRange.size.toInt) ++ data.slice(normalizedRange.end.toInt, data.length)
      data.patch(normalizedRange.start.toInt, patchData, normalizedRange.size.toInt)
    }
  }

  final case class RangeList(ranges: Seq[Range]) extends HasEmpty {
    def isEmpty: Boolean = {
      ranges.isEmpty
    }

    def size: Long = {
      ranges.map(_.size).sum
    }

    def contains(range: Range): Boolean = {
      ranges.exists(_.contains(range))
    }

    def fitToSize(dataSize: Long): RangeList = {
      copy(ranges.map(_.fitToSize(dataSize)))
    }

    def +(range: Range): RangeList = {
      copy(ranges :+ range)
    }

    def isOverlapping: Boolean = {
      ranges.sliding(2).forall {
        case Range(_, firstEnd) +: Range(secondStart, _) +: _ ⇒
          firstEnd == secondStart

        case _ ⇒
          true
      }
    }

    def toRange: Range = {
      require(isOverlapping, "Overlapping range list required")
      Range(ranges.headOption.fold(0L)(_.start), ranges.lastOption.fold(0L)(_.end))
    }

    def slice(bytes: ByteString): ByteString = {
      ranges
        .map(_.slice(bytes))
        .fold(ByteString.empty)(_ ++ _)
    }

    override def toString: String = {
      "RangeList(" + Utils.printValues(ranges, 20) + ")"
    }
  }

  object RangeList {
    def apply(firstRange: Range, otherRanges: Range*): RangeList = {
      new RangeList(firstRange +: otherRanges)
    }

    implicit def fromRange(range: Range): RangeList = {
      RangeList(Vector(range))
    }

    def zipWithRange(chunkStream: Seq[Chunk]): Seq[(Chunk, Range)] = {
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

      val rangedChunks = zipWithRange(chunkStream)

      val flatRanged = ranges.ranges.flatMap { range ⇒
        for ((chunk, fullRange) ← rangedChunks if fullRange.contains(range))
          yield (chunk, range.relativeTo(fullRange).fitToSize(chunk.checksum.size))
      }

      groupRanges(flatRanged, None, Nil)
    }
  }
}
