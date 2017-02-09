import com.karasiq.shadowcloud.index.{FolderIndex, IndexDiff}
import com.karasiq.shadowcloud.storage.IndexMerger
import org.scalatest.WordSpecLike

import scala.language.postfixOps

class IndexMergerTest extends ActorSpec with WordSpecLike {
  "Index" when {
    "empty" should {
      val index = IndexMerger()
      val diff = TestUtils.testDiff

      "add pending diff" in {
        index.addPending(diff)
        index.pending shouldBe diff
        index.diffs shouldBe empty
        index.mergedDiff shouldBe IndexDiff.empty
      }

      "remove diff" in {
        index.removePending(diff)
        index.pending shouldBe IndexDiff.empty
        index.diffs shouldBe empty
        index.mergedDiff shouldBe IndexDiff.empty
      }

      "add stored diff" in {
        index.add(diff)
        index.pending shouldBe IndexDiff.empty
        index.diffs shouldBe Vector(diff)
        index.mergedDiff shouldBe diff
        index.mergedDiff.time should not be 0
        index.chunks.chunks shouldBe diff.chunks.newChunks
        index.folders shouldBe FolderIndex.empty.patch(diff.folders)
      }
    }

    "not empty" should {
      val index = IndexMerger()
      val diff1 = TestUtils.testDiff
      val diff2 = TestUtils.randomDiff
      val diff2Reverse = diff2.reverse

      "add initial diff" in {
        index.add(diff1)
        index.diffs shouldBe Vector(diff1)
        index.mergedDiff shouldBe diff1
        index.chunks.chunks shouldBe diff1.chunks.newChunks
        index.folders shouldBe FolderIndex.empty.patch(diff1.folders)
      }

      "add second diff" in {
        index.add(diff2)
        index.diffs shouldBe Vector(diff1, diff2)
        index.mergedDiff shouldBe diff1.merge(diff2)
        index.chunks.chunks shouldBe (diff1.chunks.newChunks ++ diff2.chunks.newChunks)
        index.folders shouldBe FolderIndex.empty.patch(diff1.folders).patch(diff2.folders)
      }

      "reverse diff" in {
        index.add(diff2Reverse)
        index.diffs shouldBe Vector(diff1, diff2, diff2Reverse)
        index.mergedDiff shouldBe diff1.copy(time = diff2.time)
        index.chunks.chunks shouldBe diff1.chunks.newChunks
        index.folders shouldBe FolderIndex.empty.patch(diff1.folders).patch(diff2.folders).patch(diff2Reverse.folders)
      }
    }
  }
}
