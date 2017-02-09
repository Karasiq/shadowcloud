import java.nio.file.Files

import akka.stream.scaladsl.{Compression, Keep, Sink, Source}
import com.karasiq.shadowcloud.index.IndexDiff
import com.karasiq.shadowcloud.serialization.Serialization
import com.karasiq.shadowcloud.storage.files.FileIndexRepository
import org.scalatest.FlatSpecLike

import scala.concurrent.duration._
import scala.language.postfixOps

class IndexRepositoryTest extends ActorSpec with FlatSpecLike {
  "File repository" should "store diff" in {
    val diff = TestUtils.testDiff
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
}
