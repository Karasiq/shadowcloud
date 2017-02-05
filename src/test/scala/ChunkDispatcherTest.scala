import java.nio.file.Files

import TestUtils._
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.testkit.TestActorRef
import akka.util.{ByteString, Timeout}
import com.karasiq.shadowcloud.actors.StorageDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.{ChunkDispatcher, StorageDispatcher}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, HashingMethod}
import com.karasiq.shadowcloud.index.{Checksum, Chunk, Data}
import com.karasiq.shadowcloud.storage.FileChunkRepository
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class ChunkDispatcherTest extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(10 seconds)
  val text = ByteString("When the dispatcher invokes the processing behavior of an actor on a message, it actually calls apply on the current behavior registered for the actor.")
  val chunk = Chunk(
    Checksum(HashingMethod("SHA1"), text.length, toByteString("ca55a227aa7b739b6edb1ebde26cd6e4f0b98c6a"), text.length, toByteString("ca55a227aa7b739b6edb1ebde26cd6e4f0b98c6a")),
    EncryptionParameters.empty,
    Data(text, text)
  )
  val chunkDispatcher = TestActorRef(Props[ChunkDispatcher], "chunkDispatcher")
  val storage = TestActorRef(Props(classOf[StorageDispatcher], new FileChunkRepository(Files.createTempDirectory("cdt-storage")), chunkDispatcher), "tempDirStorage")

  "Chunk dispatcher" should "write chunk" in {
    val future = chunkDispatcher ? WriteChunk(chunk)
    future.futureValue shouldBe WriteChunk.Success(chunk)
  }

  it should "read chunk" in {
    val future = chunkDispatcher ? ReadChunk(chunk.withoutData)
    future.futureValue shouldBe ReadChunk.Success(chunk.copy(data = chunk.data.copy(plain = ByteString.empty)))
  }

  it should "deduplicate chunk" in {
    val future = chunkDispatcher ? WriteChunk(chunk.copy(encryption = chunk.encryption.copy(EncryptionMethod.AES()), data = chunk.data.copy(encrypted = randomBytes(text.length))))
    future.futureValue shouldBe WriteChunk.Success(chunk)
  }

  override protected def afterAll() = {
    actorSystem.terminate()
    super.afterAll()
  }
}
