package com.karasiq.shadowcloud.test

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.streams.FileSplitter

import scala.language.{implicitConversions, postfixOps}

// Test application
object Main extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-test")
  implicit val actorMaterializer = ActorMaterializer()

  implicit def stringAsPath(str: String): java.nio.file.Path = Paths.get(str)

  FileIO.fromPath("LICENSE")
    .via(new FileSplitter(3333, "SHA1"))
    .fold(Seq.empty[Chunk])(_ :+ _)
    .runForeach { chunks ⇒
      val file = File(Path.root, "LICENSE", chunks.map(_.size).sum, System.currentTimeMillis(), System.currentTimeMillis(), ByteString.empty, chunks.map(_.hash))
      val chunkIndex = ChunkIndex(chunks)
      val folderIndex = FolderIndex.empty.addFiles(file)
      println(chunkIndex)
      println(folderIndex)
      assert(folderIndex.folders.values.flatMap(_.files).flatMap(_.chunks).forall(chunkIndex.contains))
    }
}
