import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Compression, Keep, Sink, Source}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, HashingMethod}
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.serialization.Serialization
import com.karasiq.shadowcloud.storage.{FileIndexRepository, IndexDiff}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.duration._
import scala.language.postfixOps

class IndexRepositoryTest extends FlatSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  "File repository" should "store diff" in {
    val sha1 = HashingMethod("SHA1")
    val chunks = Seq(
      Chunk(Checksum(sha1, 3333, TestUtils.toByteString("3cbf6645aebeeacdf9f1c86753c996d045ca06e2"), 3349, TestUtils.toByteString("15e78f9f83d24153a19559bec5647c8579399535")), EncryptionParameters(EncryptionMethod.AES("GCM", 256), TestUtils.toByteString("4a7f1a7023f0414feebb6b087b6af4d1340deb808b52db1a2b6ed2ab2795bf98"), TestUtils.toByteString("8dc86add15b7248bbbf4be1a")))
    )
    val file = File(Path.root, "LICENSE", System.currentTimeMillis(), System.currentTimeMillis(), Checksum(sha1, chunks.map(_.checksum.size).sum), chunks)
    val diff = IndexDiff(System.currentTimeMillis(), Seq(FolderDiff(Path.root, newFiles = Set(file))), ChunkIndexDiff(chunks.toSet))
    val testRepository = new FileIndexRepository(Files.createTempDirectory("irp-test"))
    val future = Source.single(diff)
      .via(Serialization.toBytes())
      .via(Compression.gzip)
      .alsoToMat(Sink.ignore)(Keep.right)
      .to(testRepository.write(diff.time))
      .run()

    whenReady(future, timeout(10 seconds)) { _ ⇒
      val result = testRepository.keys
        .flatMapMerge(3, testRepository.read)
        .via(Compression.gunzip())
        .via(Serialization.fromBytes[IndexDiff]())
        .fold(Vector.empty[IndexDiff])(_ :+ _)
        .map(_.sortBy(_.time).fold(IndexDiff.empty)(_ merge _))
        .runWith(Sink.head)
      result.futureValue(timeout(10 seconds)) shouldBe diff
    }
  }

  override protected def afterAll() = {
    actorSystem.terminate()
    super.afterAll()
  }
}
