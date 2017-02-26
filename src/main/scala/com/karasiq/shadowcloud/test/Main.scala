package com.karasiq.shadowcloud.test

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Compression, FileIO, Sink, Source}
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod}
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.serialization.Serialization
import com.karasiq.shadowcloud.storage.IndexRepository
import com.karasiq.shadowcloud.streams.FileSplitter

import scala.concurrent.ExecutionContext
import scala.language.{implicitConversions, postfixOps}

// Test application
object Main extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-test")
  implicit val actorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = actorSystem.dispatcher

  implicit def stringAsPath(str: String): java.nio.file.Path = Paths.get(str)

  private val hashingMethod = HashingMethod.default
  private val encryptionMethod = EncryptionMethod.AES()
  FileIO.fromPath("LICENSE")
    .via(new FileSplitter(3333))
    .via(new ChunkEncryptor(encryptionMethod, hashingMethod))
    .via(new ChunkVerifier)
    .fold(Seq.empty[Chunk])(_ :+ _)
    .runForeach { chunks ⇒
      val chunksWithoutData = chunks.map(_.withoutData)
      val file = File(Path.root / "LICENSE", System.currentTimeMillis(), System.currentTimeMillis(), Checksum(hashingMethod, chunks.map(_.checksum.size).sum), chunksWithoutData)
      val chunkIndex = ChunkIndex(chunksWithoutData)
      val folderIndex = FolderIndex.empty.addFiles(file)
      println(chunkIndex)
      println(folderIndex)
      assert(folderIndex.folders.values.flatMap(_.files).flatMap(_.chunks).forall(chunkIndex.contains))

      val storage = IndexRepository.numeric(IndexRepository.fromDirectory(Paths.get(sys.props("shadowcloud.test.storage"))))
      val diff = IndexDiff(System.currentTimeMillis(), FolderIndexDiff.wrap(folderIndex), ChunkIndexDiff.wrap(chunkIndex))

      Source.single(diff)
        .via(Serialization.toBytes())
        .via(Compression.gzip)
        .alsoTo(Sink.onComplete { _ ⇒
          storage.keysAfter(0)
            .flatMapMerge(3, storage.read)
            .via(Compression.gunzip())
            .via(Serialization.fromBytes[IndexDiff]())
            .log("diffs")
            .fold(IndexDiff.empty)((first, second) ⇒ first.merge(second))
            .runForeach(println)
        })
        .to(storage.write(diff.time))
        .run()
    }
}
