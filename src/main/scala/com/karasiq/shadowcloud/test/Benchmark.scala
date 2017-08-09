package com.karasiq.shadowcloud.test

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success}
import scala.util.control.NonFatal

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.streams.ChunkSplitter
import com.karasiq.shadowcloud.utils.MemorySize

private object Benchmark extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-benchmark")
  implicit val timeout = Timeout(15 seconds)
  val sc = ShadowCloud(actorSystem)
  import sc.implicits._

  printBlock("Available algorithms")
  println("Encryption: " + sc.modules.crypto.encryptionAlgorithms.toSeq.sorted.mkString(", "))
  println("Signatures: " + sc.modules.crypto.signingAlgorithms.toSeq.sorted.mkString(", "))
  println("Hashing: " + sc.modules.crypto.hashingAlgorithms.toSeq.sorted.mkString(", "))

  sc.actors.regionSupervisor
  Thread.sleep(10000)

  printBlock("Read benchmark")
  for (_ ← 1 to 5) runReadBenchmark()

  printBlock("Write benchmark")
  println(sc.config.crypto)
  for (_ ← 1 to 5) runWriteBenchmark()
  //System.exit(0)

  runProviderBenchmark("libsodium", "ChaCha20/Poly1305", 256, "Blake2b")
  runProviderBenchmark("libsodium", "AES/GCM", 256, "Blake2b")
  runProviderBenchmark("bouncycastle", "ChaCha20", 256, "Blake2b")
  runProviderBenchmark("bouncycastle", "AES/GCM", 256, "Blake2b")
  runProviderBenchmark("libsodium", "XSalsa20/Poly1305", 256, "Blake2b")
  runProviderBenchmark("bouncycastle", "XSalsa20", 256, "Blake2b")
  System.exit(0)

  // Benchmarks
  private[this] def runProviderBenchmark(provider: String, encAlg: String, encKeySize: Int, hashAlg: String): Unit = {
    printBlock(s"$provider $encAlg[$encKeySize]/$hashAlg benchmark")
    val encMethod = EncryptionMethod(encAlg, encKeySize, provider = provider)
    val hashMethod = HashingMethod(hashAlg, provider = provider)
    runWriteBenchmark(encMethod, hashMethod, HashingMethod.none)
    runWriteBenchmark(encMethod, hashMethod, hashMethod) // Double hashing
  }

  private[this] def runWriteBenchmark(encryption: EncryptionMethod = sc.config.crypto.encryption.chunks,
                                      hashing: HashingMethod = sc.config.crypto.hashing.chunks,
                                      fileHashing: HashingMethod = sc.config.crypto.hashing.files): Unit = {
    val modifier = 1
    val chunkSize = MemorySize.MB * modifier
    val chunkCount = 1024 / modifier
    val mbCount = chunkCount * (chunkSize.toDouble / MemorySize.MB)
    println(s"Starting write benchmark (${MemorySize.toString(chunkSize)}): $encryption/$hashing/$fileHashing")

    try {
      val startTime = System.nanoTime()
      val promise = Promise[Done]
      randomBytesSource(chunkSize)
        .via(ChunkSplitter(chunkSize))
        .take(chunkCount)
        .via(sc.streams.chunk.beforeWrite(encryption, hashing, HashingMethod.none))
        // .log("chunks", _.checksum)
        // .addAttributes(ActorAttributes.logLevels(Logging.InfoLevel))
        .alsoTo(sc.streams.chunk.index(fileHashing, HashingMethod.none))
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
      Await.result(promise.future, Duration.Inf)
    } catch {
      case NonFatal(exc) ⇒ println(s"Benchmark failed: $exc")
    }
  }

  private[this] def runReadBenchmark(): Unit = {
    val start = System.nanoTime()
    val future = Source.fromFuture(sc.ops.region.getIndex("testRegion"))
      .map(IndexMerger.restore(RegionKey.zero, _))
      .map(_.folders.folders.values.flatMap(_.files).maxBy(_.checksum.size))
      .flatMapConcat(file ⇒ sc.streams.file.read("testRegion", file))
      .map(_.length)
      .runWith(Sink.fold(0L)(_ + _))

    val bytes = Await.result(future, Duration.Inf)
    val elapsed = (System.nanoTime() - start).nanos
    val perMb = elapsed / (bytes / MemorySize.MB)
    val speed = 1.second / perMb
    println(f"Read benchmark completed, ${MemorySize.toString(bytes)}, ${elapsed.toSeconds} seconds elapsed, ${perMb.toMillis} ms per megabyte, $speed%.2f MB/sec")
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

  private[this] def printBlock(str: String): Unit = {
    println()
    println("----------------------------------------------------------------------")
    println(str.capitalize)
    println("----------------------------------------------------------------------")
  }
}
