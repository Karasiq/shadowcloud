package com.karasiq.shadowcloud.test

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.streams.FileSplitter

import scala.language.{implicitConversions, postfixOps}

// Test application
object Main extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-test")
  implicit val actorMaterializer = ActorMaterializer()

  implicit def stringAsPath(str: String): java.nio.file.Path = Paths.get(str)

  FileIO.fromPath("LICENSE")
    .via(new FileSplitter(3333, HashingMethod("SHA-512")))
    .fold(Seq.empty[Chunk])(_ :+ _)
    .runForeach { chunks ⇒
      val file = File(Path.root, "LICENSE", System.currentTimeMillis(), System.currentTimeMillis(), Checksum(size = chunks.map(_.checksum.size).sum), chunks.map(_.withoutData))
      val chunkIndex = ChunkIndex(chunks)
      val folderIndex = FolderIndex.empty.addFiles(file)
      println(chunkIndex)
      println(folderIndex)
      assert(folderIndex.folders.values.flatMap(_.files).flatMap(_.chunks).map(_.checksum.hash).forall(chunkIndex.contains))
    }
}
