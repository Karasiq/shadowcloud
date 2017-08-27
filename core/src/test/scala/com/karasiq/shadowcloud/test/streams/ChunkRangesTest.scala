package com.karasiq.shadowcloud.test.streams

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.test.utils.TestUtils

class ChunkRangesTest extends FlatSpec with Matchers {
  "Chunk range" should "contain sub-range" in {
    val range1 = ChunkRanges.Range(10, 20)
    val range2 = ChunkRanges.Range(8, 15)
    val range3 = ChunkRanges.Range(0, 10)
    val range4 = ChunkRanges.Range(20, 25)
    range1.contains(range2) shouldBe true
    range1.contains(range3) shouldBe false
    range1.contains(range4) shouldBe false
  }

  it should "generate relative range" in {
    val range1 = ChunkRanges.Range(10, 20)
    val range2 = ChunkRanges.Range(8, 15)
    val range3 = range2.relativeTo(range1)
    range3 shouldBe ChunkRanges.Range(-2, 5)
  }

  it should "create length" in {
    val range = ChunkRanges.Range(0, 1000)
    range.length shouldBe 1000
  }

  it should "split byte string" in {
    val range1 = ChunkRanges.Range(10, 20)
    val bytes = TestUtils.randomBytes(20)
    range1.slice(bytes) shouldBe bytes.drop(10).take(10)
  }

  it should "process chunk stream" in {
    val ranges = ChunkRanges.RangeList(
      ChunkRanges.Range(10, 20),
      ChunkRanges.Range(5, 10),
      ChunkRanges.Range(80, 150),
      ChunkRanges.Range(300, 999999)
    )

    val chunks = TestUtils.indexedBytes._2.chunks
    val result = ChunkRanges.RangeList.mapChunkStream(ranges, chunks)
    val expected = Seq(
      (chunks(0), ChunkRanges.RangeList(ChunkRanges.Range(10, 20), ChunkRanges.Range(5, 10), ChunkRanges.Range(80, 100))),
      (chunks(1), ChunkRanges.RangeList(ChunkRanges.Range(0, 50))),
      (chunks(3), ChunkRanges.RangeList(ChunkRanges.Range(0, 56)))
    )

    result shouldBe expected
  }

  it should "apply ranges to bytes" in {
    val bytes = TestUtils.indexedBytes._1
    val ranges = ChunkRanges.RangeList(
      ChunkRanges.Range(10, 20),
      ChunkRanges.Range(5, 10),
      ChunkRanges.Range(80, 150)
    )
    val expected = bytes.slice(10, 20) ++ bytes.slice(5, 10) ++ bytes.slice(80, 150)
    ranges.length shouldBe expected.length
    ranges.slice(bytes) shouldBe expected
  }

  it should "throw exception on invalid range" in {
    intercept[IllegalArgumentException](ChunkRanges.Range(10, 0))
  }
}
