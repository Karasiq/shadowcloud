import com.karasiq.shadowcloud.index.IndexDiff
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
        index.stored shouldBe IndexDiff.empty
      }

      "remove diff" in {
        index.removePending(diff)
        index.pending shouldBe IndexDiff.empty
        index.stored shouldBe IndexDiff.empty
      }

      "add stored diff" in {
        index.add(diff)
        index.pending shouldBe IndexDiff.empty
        index.stored shouldBe diff
      }

      // TODO: Conflicts
    }
  }
}
