package com.karasiq.shadowcloud.test

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import akka.{Done, NotUsed}
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.streams.{ChunkProcessing, ChunkSplitter}
import com.karasiq.shadowcloud.utils.MemorySize

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.language.postfixOps
import scala.util.control.NonFatal
import scala.util.{Failure, Random, Success}

private object Benchmark extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-benchmark")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val timeout = Timeout(15 seconds)
  val config = AppConfig(actorSystem)
  val chunkProcessing = ChunkProcessing(config)(actorSystem.dispatcher)

  // Start
  runWriteBenchmark()
  runWriteBenchmark(fileHashing = HashingMethod.none) // Single hashing
  runWriteBenchmark(EncryptionMethod("Salsa20", 256, provider = "libsodium"),
    HashingMethod("SHA256", provider = "libsodium"), HashingMethod.none)
  System.exit(0)

  // Benchmarks
  private[this] def runWriteBenchmark(encryption: EncryptionMethod = config.crypto.encryption.chunks,
                                      hashing: HashingMethod = config.crypto.hashing.chunks,
                                      fileHashing: HashingMethod = config.crypto.hashing.files): Unit = {
    val chunkSize = MemorySize.MB
    val chunkCount = 1024
    val mbCount = chunkCount * (chunkSize.toDouble / MemorySize.MB)
    println(s"Starting write benchmark: $encryption/$hashing/$fileHashing")
    
    val startTime = System.nanoTime()
    val promise = Promise[Done]
    randomBytesSource(chunkSize)
      .via(ChunkSplitter(chunkSize))
      .take(chunkCount)
      .via(chunkProcessing.beforeWrite(encryption, hashing))
      .alsoTo(chunkProcessing.index(fileHashing))
      .runWith(Sink.onComplete {
        case Success(Done) ⇒
          val elapsed = (System.nanoTime() - startTime).nanos
          val perMb = elapsed / mbCount
          val speed = 1.second / perMb
          println(f"Write benchmark completed, ${elapsed.toSeconds} seconds elapsed, ${perMb.toMillis} ms per megabyte, $speed%.2f MB/sec")
          promise.success(Done)

        case Failure(error) ⇒
          promise.failure(error)
      })
    try {
      Await.result(promise.future, Duration.Inf)
    } catch {
      case NonFatal(exc) ⇒ println(s"Benchmark failed: $exc")
    }
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
