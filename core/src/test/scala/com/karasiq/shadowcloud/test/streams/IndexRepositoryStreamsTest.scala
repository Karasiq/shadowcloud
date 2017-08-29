package com.karasiq.shadowcloud.test.streams

import java.nio.file.Files

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import org.scalatest.FlatSpecLike

import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.model.crypto.EncryptionMethod
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.repository.{KeyValueRepository, PathTreeRepository, RepositoryKeys}
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams}
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, SCExtensionSpec, TestUtils}

class IndexRepositoryStreamsTest extends SCExtensionSpec with FlatSpecLike {
  "In-memory repository" should "store diff" in {
    testRepository(Repositories.inMemory)
  }

  "File repository" should "store diff" in {
    testRepository(PathTreeRepository.toKeyValue(Repositories.fromDirectory(Files.createTempDirectory("irp-test")), Path.root / "default"))
  }

  it should "validate path" in {
    intercept[IllegalArgumentException](Repositories.fromDirectory(Files.createTempFile("irp-test", "file")))
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()

    // Test symmetric index encryption
    Await.result(sc.keys.provider.addKeySet(sc.keys.generateKeySet(EncryptionMethod("AES/GCM", 256))), 5 seconds)
  }

  private[this] def testRepository(repository: KeyValueRepository): Unit = {
    val diff = CoreTestUtils.randomDiff
    val testRepository = RepositoryKeys.toLong(repository)

    // Write diff
    val streams = IndexRepositoryStreams(CoreTestUtils.storageConfig("testStorage"), system)
    val (write, writeResult) = TestSource.probe[(Long, IndexData)]
      .via(streams.write(testRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    write.sendNext((diff.time, IndexData("testRegion", diff.time, diff)))
    writeResult.request(2)
    val IndexIOResult(diff.time, IndexData("testRegion", diff.time, `diff`), StorageIOResult.Success(_, _)) = writeResult.expectNext()
    write.sendComplete()
    writeResult.expectComplete()

    // Enumerate diffs
    val keys = testRepository.keys.runWith(TestSink.probe)
    keys.requestNext(diff.time)
    keys.request(1)
    keys.expectComplete()

    // Read diff
    val (read, readResult) = TestSource.probe[Long]
      .via(streams.read(testRepository))
      .toMat(TestSink.probe)(Keep.both)
      .run()
    read.sendNext(diff.time)
    val IndexIOResult(diff.time, IndexData("testRegion", diff.time, `diff`), StorageIOResult.Success(_, diffBytes)) = readResult.requestNext()
    diffBytes should not be 0
    read.sendComplete()
    readResult.expectComplete()

    // Rewrite error
    val rewriteResult = Source.single(TestUtils.randomBytes(200))
      .runWith(testRepository.write(diff.time))

    whenReady(rewriteResult) { result ⇒
      result.isFailure shouldBe true
    }

    val deleteResult = Source.single(diff.time)
      .via(streams.delete(testRepository))
      .runWith(Sink.head)

    whenReady(deleteResult) { result ⇒
      val IndexIOResult(key, _, StorageIOResult.Success(_, count)) = result 
      key shouldBe diff.time
      count shouldBe diffBytes
      val keys = testRepository.keys.runWith(TestSink.probe)
      keys.request(1)
      keys.expectComplete()
    }
  }
}
