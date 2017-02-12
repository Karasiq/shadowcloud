import java.nio.file.Files

import TestUtils._
import akka.pattern.ask
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.TestActorRef
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.StorageDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.{ChunkDispatcher, StorageDispatcher}
import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.karasiq.shadowcloud.storage.files.FileChunkRepository
import org.scalatest.FlatSpecLike

import scala.language.postfixOps

class ChunkDispatcherTest extends ActorSpec with FlatSpecLike {
  val chunk = TestUtils.testChunk
  val chunkDispatcher = TestActorRef(ChunkDispatcher.props, "chunkDispatcher")
  val storage = TestActorRef(StorageDispatcher.props(new FileChunkRepository(Files.createTempDirectory("cdt-storage")), chunkDispatcher), "tempDirStorage")

  "Chunk dispatcher" should "write chunk" in {
    val future = chunkDispatcher ? WriteChunk(chunk)
    future.futureValue shouldBe WriteChunk.Success(chunk)
  }

  it should "read chunk" in {
    val future = chunkDispatcher ? ReadChunk(chunk.withoutData)
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
    val future = chunkDispatcher ? WriteChunk(chunk.copy(encryption = chunk.encryption.copy(EncryptionMethod.AES()), data = chunk.data.copy(encrypted = randomBytes(chunk.data.plain.length))))
    future.futureValue shouldBe WriteChunk.Success(chunk)
  }
}
