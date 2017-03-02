package com.karasiq.shadowcloud.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.streams.{ChunkProcessing, ChunkSplitter}
import com.karasiq.shadowcloud.utils.MemorySize

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}

private object Benchmark extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-benchmark")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(15 seconds)
  val chunkProcessing = ChunkProcessing(actorSystem)

  // Start
  startWriteBenchmark()

  // Benchmarks
  private[this] def startWriteBenchmark(encryptionMethod: EncryptionMethod = EncryptionMethod.default, hashingMethod: HashingMethod = HashingMethod.default): Unit = {
    val chunkSize = MemorySize.MB
    val chunkCount = 1024
    val mbCount = chunkCount * (chunkSize.toDouble / MemorySize.MB)
    val startTime = System.nanoTime()
    randomBytesSource(chunkSize)
      .via(ChunkSplitter(chunkSize))
      .take(chunkCount)
      .via(chunkProcessing.beforeWrite(encryptionMethod, hashingMethod))
      .alsoTo(chunkProcessing.index())
      .runWith(Sink.onComplete {
        case Success(Done) ⇒
          val elapsed = (System.nanoTime() - startTime).nanos
          val perMb = elapsed / mbCount
          val speed = 1.second / perMb
          println(f"Write benchmark completed, ${elapsed.toSeconds} seconds elapsed, ${perMb.toMillis} ms per megabyte, $speed%.2f MB/sec")
          actorSystem.terminate()

        case Failure(error) ⇒
          actorSystem.terminate()
          throw error
      })
  }

  // Utils
  private[this] def randomBytesSource(size: Int): Source[ByteString, NotUsed] = {
    Source.fromIterator(() ⇒ {
      val bytes = Array.ofDim[Byte](size)
      Iterator.continually {
        Random.nextBytes(bytes)
        ByteString(bytes)
      }
    })
  }
}
