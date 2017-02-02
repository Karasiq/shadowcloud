import TestUtils._
import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.index.{Checksum, Chunk, Data}
import com.karasiq.shadowcloud.streams._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.postfixOps

class FileSplitterTest extends FlatSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  val text = ByteString("""You may have noticed various code patterns that emerge when testing stream pipelines. Akka Stream has a separate akka-stream-testkit module that provides tools specifically for writing stream tests. This module comes with two main components that are TestSource and TestSink which provide sources and sinks that materialize to probes that allow fluent API.""")
  val textHash = "2f5a0c419cfeb92f05888ae3468e54fee3ee1726"
  val preCalcHashes = Vector("f660847d03634f41c45f7be337b02973a083721a", "dfa6cbe4eb725d390e3339075fe420791a5a394f", "e63bf72054623e911ce6a995dc520527d7fe2e2d", "802f6e7f54ca13c650741e65f188b0bdb023cb15")
  val hashingMethod = HashingMethod("SHA1")

  "File splitter" should "split text" in {
    val fullOut = Source.single(text)
      .via(new FileSplitter(100, hashingMethod))
      .map(_.checksum.hash.toHexString)
      .runWith(Sink.seq)

    fullOut.futureValue shouldBe preCalcHashes
  }

  it should "join text" in {
    val (in, out) = TestSource.probe[ByteString]
      .via(new FileSplitter(100, hashingMethod))
      .map(_.checksum.hash.toHexString)
      .toMat(TestSink.probe)(Keep.both)
      .run()

    in.sendNext(text.take(10)).sendNext(text.slice(10, 110))
    out.requestNext(preCalcHashes(0))
    in.sendNext(text.slice(110, 300))
    out.request(2).expectNext(preCalcHashes(1), preCalcHashes(2))
    in.sendNext(text.drop(300))
    in.sendComplete()
    out.requestNext(preCalcHashes(3))
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

    val (file, chunks) = Source.single(text)
      .via(new FileSplitter(100, hashingMethod))
      .via(new ChunkEncryptor(EncryptionMethod.AES(), hashingMethod))
      .via(new ChunkVerifier)
      .viaMat(new FileIndexer(hashingMethod))(Keep.right)
      .map(testChunk) // Verify again
      .toMat(Sink.seq)(Keep.both)
      .run()

    whenReady(chunks) { chunks ⇒
      chunks.map(_.checksum.hash.toHexString) shouldBe preCalcHashes
      val future = Source(chunks)
        .via(new ChunkDecryptor)
        .via(new ChunkVerifier)
        .map(testChunk)
        .runWith(Sink.ignore)

      future.futureValue shouldBe Done
    }

    whenReady(file) { file ⇒
      file.checksum.hash.toHexString shouldBe textHash
      file.chunks.map(_.checksum.hash.toHexString) shouldBe preCalcHashes
    }
  }

  override protected def afterAll() = {
    actorSystem.terminate()
    super.afterAll()
  }
}
