import TestUtils._
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.HashingModule
import com.karasiq.shadowcloud.streams.FileSplitter
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.language.postfixOps

class FileSplitterTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  implicit val actorSystem = ActorSystem()
  implicit val actorMaterializer = ActorMaterializer()

  "File splitter" should "split text and calculate SHA-1" in {
    val text = ByteString("""You may have noticed various code patterns that emerge when testing stream pipelines. Akka Stream has a separate akka-stream-testkit module that provides tools specifically for writing stream tests. This module comes with two main components that are TestSource and TestSink which provide sources and sinks that materialize to probes that allow fluent API.""")

    val (in, out) = TestSource.probe[ByteString]
      .via(new FileSplitter(100, HashingModule("SHA1")))
      .map(_.hash.toHexString)
      .toMat(TestSink.probe[String])(Keep.both)
      .run()

    in.sendNext(text.take(10)).sendNext(text.slice(10, 110))
    out.requestNext("f660847d03634f41c45f7be337b02973a083721a")
    in.sendNext(text.slice(110, 300))
    out.request(2).expectNext("dfa6cbe4eb725d390e3339075fe420791a5a394f", "e63bf72054623e911ce6a995dc520527d7fe2e2d")
    in.sendNext(text.drop(300))
    in.sendComplete()
    out.requestNext("802f6e7f54ca13c650741e65f188b0bdb023cb15")
    out.expectComplete()
  }

  override protected def afterAll() = {
    actorSystem.terminate()
    super.afterAll()
  }
}
