package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.{Compression, Flow}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.serialization.Serialization
import com.karasiq.shadowcloud.storage.internal.DefaultIndexRepositoryStreams
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.language.postfixOps

trait IndexRepositoryStreams {
  def write[Key](repository: IndexRepository[Key]): Flow[(Key, IndexDiff), (Key, IndexDiff), _]
  def read[Key](repository: IndexRepository[Key]): Flow[Key, (Key, IndexDiff), _]
}

// TODO: Encryption, signatures
object IndexRepositoryStreams {
  private object Flows {
    val write = Flow[IndexDiff].via(Serialization.toBytes())
    val read = Flow[ByteString].via(ByteStringConcat()).via(Serialization.fromBytes[IndexDiff]())
    val gzipWrite = write.via(Compression.gzip)
    val gzipRead = Flow[ByteString].via(Compression.gunzip()).via(read)
  }

  def create(breadth: Int, writeFlow: Flow[IndexDiff, ByteString, _],
             readFlow: Flow[ByteString, IndexDiff, _]): IndexRepositoryStreams = {
    new DefaultIndexRepositoryStreams(breadth, writeFlow, readFlow)
  }

  val default = create(3, Flows.write, Flows.read)
  val gzipped = create(3, Flows.gzipWrite, Flows.gzipRead)
}
