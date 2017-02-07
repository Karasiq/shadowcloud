import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingModule}
import com.karasiq.shadowcloud.index.{Checksum, Chunk, Data}
import com.karasiq.shadowcloud.streams._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.postfixOps

//noinspection ZeroIndexToHead
class FileSplitterTest extends FlatSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  val (sourceBytes, sourceFile) = TestUtils.indexedBytes
  val hashingMethod = sourceFile.checksum.method
  val sourceHashes = sourceFile.chunks.map(_.checksum.hash)

  "File splitter" should "split text" in {
    val fullOut = Source.single(sourceBytes)
      .via(new FileSplitter(100, hashingMethod))
      .map(_.checksum.hash)
      .runWith(Sink.seq)

    fullOut.futureValue shouldBe sourceFile.chunks.map(_.checksum.hash)
  }

  it should "join text" in {
    val (in, out) = TestSource.probe[ByteString]
      .via(new FileSplitter(100, sourceFile.checksum.method))
      .map(_.checksum.hash)
      .toMat(TestSink.probe)(Keep.both)
      .run()

    in.sendNext(sourceBytes.take(10)).sendNext(sourceBytes.slice(10, 110))
    out.requestNext(sourceHashes(0))
    in.sendNext(sourceBytes.slice(110, 300))
    out.request(2).expectNext(sourceHashes(1), sourceHashes(2))
    in.sendNext(sourceBytes.drop(300))
    in.sendComplete()
    out.requestNext(sourceHashes(3))
    out.expectComplete()
  }

  "Chunk encryptor" should "encrypt chunk stream" in {
    def testChunk(chunk: Chunk) = {
      val hasher = HashingModule(chunk.checksum.method)
      val decryptor = EncryptionModule(chunk.encryption.method)
      val hash1 = hasher.createHash(chunk.data.plain)
      val hash2 = hasher.createHash(chunk.data.encrypted)
      val size1 = chunk.data.plain.length
      val size2 = chunk.data.encrypted.length
      val data = decryptor.decrypt(chunk.data.encrypted, chunk.encryption)
      chunk shouldBe Chunk(Checksum(chunk.checksum.method, size1, hash1, size2, hash2), chunk.encryption, Data(data, chunk.data.encrypted))
      chunk
    }

    val (file, chunks) = Source.single(sourceBytes)
      .via(new FileSplitter(100, hashingMethod))
      .via(new ChunkEncryptor(EncryptionMethod.AES(), hashingMethod))
      .via(new ChunkVerifier)
      .viaMat(new FileIndexer(hashingMethod))(Keep.right)
      .map(testChunk) // Verify again
      .toMat(Sink.seq)(Keep.both)
      .run()

    whenReady(chunks) { chunks ⇒
      chunks.map(_.checksum.hash) shouldBe sourceHashes
      val future = Source(chunks)
        .via(new ChunkDecryptor)
        .via(new ChunkVerifier)
        .map(testChunk)
        .runWith(Sink.ignore)

      future.futureValue shouldBe Done
    }

    whenReady(file) { file ⇒
      file.checksum.hash shouldBe sourceFile.checksum.hash
      file.chunks.map(_.checksum.hash) shouldBe sourceHashes
    }
  }

  override protected def afterAll() = {
    actorSystem.terminate()
    super.afterAll()
  }
}
