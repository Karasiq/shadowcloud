package com.karasiq.shadowcloud.server.http.test

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.headers.{ByteRange, Range, RawHeader}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.util.ByteString
import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.api.{SCApiEncoding, SCApiUtils}
import com.karasiq.shadowcloud.server.http.SCAkkaHttpRoutes
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.test.utils.TestUtils
import com.karasiq.shadowcloud.{ShadowCloud, ShadowCloudExtension}
import org.scalatest.{FlatSpec, Matchers, SequentialNestedSuiteExecution}

import scala.concurrent.duration._
import scala.reflect.ClassTag

class SCHttpServerTest extends FlatSpec with Matchers with ScalatestRouteTest with SequentialNestedSuiteExecution {
  implicit val sc = ShadowCloud(system)

  object TestServer extends SCAkkaHttpRoutes with Directives {
    protected val sc: ShadowCloudExtension = SCHttpServerTest.this.sc
  }

  import TestServer._
  import SCApiInternals.apiEncoding
  import akka.http.scaladsl.unmarshalling.Unmarshaller._

  implicit val routeTimeout = RouteTestTimeout(30 seconds)

  val testRegionId  = "testRegion"
  val testStorageId = "testStorage"

  val route                       = Route.seal(TestServer.scRoutes)
  val (testFileContent, testFile) = TestUtils.indexedBytes
  val encodedPath                 = SCApiEncoding.toUrlSafe(apiEncoding.encodePath(testFile.path))

  "Test server" should "upload file" in {
    val request = Post(s"/upload/$testRegionId/$encodedPath", HttpEntity(testFileContent))
      .copy(headers = List(RawHeader("X-Requested-With", SCApiUtils.RequestedWith)))

    request ~> route ~> check {
      val file =
        apiEncoding.decodeFile(entityAs[ByteString](implicitly[FromEntityUnmarshaller[ByteString]], implicitly[ClassTag[ByteString]], 30 seconds))
      file.path shouldBe testFile.path
      file.revision shouldBe 0
      file.checksum.size shouldBe testFileContent.length
      HexString.encode(file.chunks.head.checksum.hash) shouldBe "8860045877c0e22e2353e85b0f7c59cfc80cb98510dcd9a40fccd775c355a238"
    }
  }

  it should "download file" in {
    val request = Get(s"/download/$testRegionId/$encodedPath/test.txt")
    request ~> route ~> check {
      entityAs[ByteString] shouldBe testFileContent
    }
  }

  it should "download file parts" in {
    val request = Get(s"/download/$testRegionId/$encodedPath/test.txt")
      .copy(headers = List(Range(ByteRange(0, 99), ByteRange(100, 199))))

    request ~> route ~> check {
      val expectedContent =
        testFileContent.slice(0, 100) ++
          testFileContent.slice(100, 200)

      entityAs[ByteString] shouldBe expectedContent
    }
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    registerRegionAndStorages()
    Thread.sleep(1000)
  }

  private[this] def registerRegionAndStorages(): Unit = {
    sc.ops.supervisor.createRegion(testRegionId, sc.configs.regionConfig(testRegionId))
    sc.ops.supervisor.createStorage(testStorageId, StorageProps.inMemory)
    sc.ops.supervisor.register(testRegionId, testStorageId)
  }
}
