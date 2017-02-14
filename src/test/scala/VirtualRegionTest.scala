import java.nio.file.Files

import TestUtils._
import akka.pattern.ask
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.ChunkStorageDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.{IndexedStorageDispatcher, VirtualRegionDispatcher}
import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.storage.files.{FileChunkRepository, FileIndexRepository}
import org.scalatest.FlatSpecLike

import scala.language.postfixOps

class VirtualRegionTest extends ActorSpec with FlatSpecLike {
  val chunk = TestUtils.testChunk
  val indexedStorage = TestActorRef(IndexedStorageDispatcher.props("testStorage", new FileChunkRepository(Files.createTempDirectory("vrt-storage")), new FileIndexRepository(Files.createTempDirectory("vrt-index"))), "testStorage")
  val testRegion = TestActorRef(VirtualRegionDispatcher.props, "testRegion")

  "Virtual region" should "register storage" in {
    testRegion ! VirtualRegionDispatcher.Register("testStorage", indexedStorage)
  }

  it should "write chunk" in {
    val result = testRegion ? WriteChunk(chunk)
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
  }

  it should "read chunk" in {
    val future = testRegion ? ReadChunk(chunk.withoutData)
    whenReady(future) {
      case ReadChunk.Success(_, source) ⇒
        val probe = source
          .fold(ByteString.empty)(_ ++ _)
          .runWith(TestSink.probe)
        probe.requestNext(chunk.data.encrypted)
        probe.expectComplete()
    }
  }

  it should "deduplicate chunk" in {
    val result = testRegion ? WriteChunk(chunk.copy(encryption = chunk.encryption.copy(EncryptionMethod.AES()), data = chunk.data.copy(encrypted = randomBytes(chunk.data.plain.length))))
    result.futureValue shouldBe WriteChunk.Success(chunk, chunk)
  }
}
