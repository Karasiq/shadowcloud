package com.karasiq.shadowcloud.test.index

import com.karasiq.shadowcloud.exceptions.SCExceptions
import com.karasiq.shadowcloud.index.FolderIndex
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.model.{Folder, Path}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, TestUtils}
import org.scalatest.{Matchers, WordSpec}

class IndexMergerTest extends WordSpec with Matchers {
  "Index" when {
    "empty" should {
      val index = IndexMerger.sequential()
      val diff  = TestUtils.testDiff

      "add pending diff" in {
        index.addPending(diff)
        index.pending shouldBe diff
        index.diffs shouldBe empty
        index.mergedDiff shouldBe IndexDiff.empty
      }

      "remove diff" in {
        index.deletePending(diff)
        index.pending shouldBe IndexDiff.empty
        index.diffs shouldBe empty
        index.mergedDiff shouldBe IndexDiff.empty
      }

      "add stored diff" in {
        index.add(diff.time, diff)
        index.pending shouldBe IndexDiff.empty
        index.diffs shouldBe Map(diff.time → diff)
        index.mergedDiff shouldBe diff
        index.mergedDiff.time should not be 0
        index.chunks.chunks shouldBe diff.chunks.newChunks
        index.folders shouldBe FolderIndex.empty.patch(diff.folders)
      }
    }

    "not empty" should {
      val index        = IndexMerger.sequential()
      val diff1        = TestUtils.testDiff
      val diff2        = CoreTestUtils.randomDiff
      val diff2Reverse = diff2.reverse.copy(time = diff2.time + 1)

      "add initial diff" in {
        index.add(diff1.time, diff1)
        index.diffs shouldBe Map(diff1.time → diff1)
        index.mergedDiff shouldBe diff1
        index.chunks.chunks shouldBe diff1.chunks.newChunks
        index.folders shouldBe FolderIndex.empty.patch(diff1.folders)
      }

      "add second diff" in {
        index.add(diff2.time, diff2)
        index.diffs shouldBe Map(diff1.time → diff1, diff2.time → diff2)
        index.mergedDiff shouldBe diff1.merge(diff2)
        index.chunks.chunks shouldBe (diff1.chunks.newChunks ++ diff2.chunks.newChunks)
        index.folders shouldBe FolderIndex.empty.patch(diff1.folders).patch(diff2.folders)
      }

      "reverse diff" in {
        index.add(diff2Reverse.time, diff2Reverse)
        index.diffs shouldBe Map(diff1.time → diff1, diff2.time → diff2, diff2Reverse.time → diff2Reverse)
        index.mergedDiff.creates shouldBe diff1.copy(
          time = diff2Reverse.time,
          folders = diff1.folders.copy(diff1.folders.folders.map(f ⇒ f.copy(time = diff2.time)))
        )
        index.chunks.chunks shouldBe diff1.chunks.newChunks
        index.folders shouldBe FolderIndex.empty.patch(diff1.folders).patch(diff2.folders).patch(diff2Reverse.folders)
      }

      "throw exception on diff rewrite" in {
        intercept[SCExceptions.DiffConflict] {
          index.add(diff1.time, diff1.merge(CoreTestUtils.randomDiff))
        }
      }

      "clear" in {
        index.clear()
        index.diffs shouldBe empty
        index.pending shouldBe IndexDiff.empty
        index.mergedDiff shouldBe IndexDiff.empty
        index.lastSequenceNr shouldBe 0L
        index.chunks.chunks shouldBe empty
        index.folders shouldBe FolderIndex.empty
      }
    }

    "merging folders" should {
      "add and delete folder" in {
        val index = IndexMerger.sequential()
        val diff1 = IndexDiff(folders = FolderIndexDiff.createFolders(Folder("/1"), Folder("/1/2")))
        val diff2 = IndexDiff(folders = FolderIndexDiff.deleteFolderPaths("/1/2"))
        index.addPending(diff1)
        index.addPending(diff2)
        index.add(1, diff1)
        index.add(2, diff2)
        index.folders.patch(index.pending.folders).folders.keySet shouldBe Set[Path](Path.root, "/1")
      }

      "delete folder tree" in {
        val index = IndexMerger.sequential()
        val diff1 = IndexDiff(folders =
          FolderIndexDiff.createFolders(
            Folder("/1", files = Set(CoreTestUtils.randomFile("/1"))),
            Folder("/1/2", files = Set(CoreTestUtils.randomFile("/1/2"))),
            Folder("/1/2/3", files = Set(CoreTestUtils.randomFile("/1/2/3")))
          )
        )
        val diff2 = IndexDiff(folders = FolderIndexDiff.deleteFolderPaths("/1"))

        index.addPending(diff1)
        index.addPending(diff2)
        index.add(1, diff1.merge(diff2))
        index.folders.folders.keySet shouldBe Set(Path.root)
        index.folders.folders(Path.root) shouldBe empty
        index.mergedDiff.creates.folders.folders shouldBe empty
        index.pending.folders.folders shouldBe empty
      }

      "delete nested file" in {
        val index = IndexMerger.sequential()
        val diff1 = IndexDiff(folders =
          FolderIndexDiff.createFolders(
            Folder("/1", files = Set(CoreTestUtils.randomFile("/1"))),
            Folder("/1/2/4/5/6/7/8", files = Set(CoreTestUtils.randomFile("/1/2/4/5/6/7/8")))
          )
        )
        val diff2 = IndexDiff(folders = FolderIndexDiff.deleteFolderPaths("/1"))

        index.addPending(diff1)
        index.addPending(diff2)
        index.add(1, diff1.merge(diff2))
        index.folders.folders.keySet shouldBe Set(Path.root)
        index.folders.folders(Path.root).folders shouldBe empty
        index.mergedDiff.creates.folders.folders shouldBe empty
        index.pending.folders.folders shouldBe empty
      }
    }
  }
}
