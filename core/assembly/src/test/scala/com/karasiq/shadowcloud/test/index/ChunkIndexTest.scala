package com.karasiq.shadowcloud.test.index



import com.karasiq.shadowcloud.index.ChunkIndex
import com.karasiq.shadowcloud.test.utils.CoreTestUtils
import org.scalatest.{FlatSpec, Matchers}

class ChunkIndexTest extends FlatSpec with Matchers {
  val chunk = CoreTestUtils.randomChunk

  "Chunk index" should "add chunk" in {
    val index = ChunkIndex.empty.addChunks(chunk)
    index.chunks shouldBe Set(chunk)
  }

  it should "delete chunk" in {
    val index = ChunkIndex(Set(chunk))
    val index1 = index.deleteChunks(chunk)
    index1.chunks shouldBe empty
  }

  it should "delete chunk without data" in {
    val index = ChunkIndex(Set(chunk))
    val index1 = index.deleteChunks(chunk.withoutData)
    index1.chunks shouldBe empty
  }

  it should "merge" in {
    val index = ChunkIndex(Set(chunk))
    val chunk1 = CoreTestUtils.randomChunk
    val index1 = ChunkIndex(Set(chunk1))
    val merged = index.merge(index1)
    merged.chunks shouldBe Set(chunk, chunk1)
  }
}
