import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Compression, Keep, Sink, Source}
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

  override protected def afterAll() = {
    actorSystem.terminate()
    super.afterAll()
  }
}
