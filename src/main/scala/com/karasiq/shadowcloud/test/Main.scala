package com.karasiq.shadowcloud.test

import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import com.karasiq.shadowcloud.streams.FileSplitter
import org.apache.commons.codec.binary.Hex

import scala.language.{implicitConversions, postfixOps}

// Test application
object Main extends App {
  implicit val actorSystem = ActorSystem("shadowcloud-test")
  implicit val actorMaterializer = ActorMaterializer()

  implicit def stringAsPath(str: String): java.nio.file.Path = Paths.get(str)

  FileIO.fromPath("LICENSE")
    .via(new FileSplitter(3333, "SHA1"))
    .zipWithIndex
    .runForeach { case (chunk, index) ⇒
      Source.single(chunk.data)
        .runWith(FileIO.toPath(s"chunk-$index-${Hex.encodeHexString(chunk.hash.toArray)}"))
    }
}
