package com.karasiq.shadowcloud.storage

import akka.NotUsed
import akka.stream.scaladsl.{Compression, Flow, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.IndexDiff
import com.karasiq.shadowcloud.serialization.Serialization

import scala.language.postfixOps

trait IndexRepositoryStreams {
  def write[Key](repository: IndexRepository[Key]): Flow[(Key, IndexDiff), (Key, IndexDiff), _]
  def read[Key](repository: IndexRepository[Key]): Flow[Key, (Key, IndexDiff), _]
}

object IndexRepositoryStreams {
  // TODO: Encryption, signatures
  private final class DefaultIndexRepositoryStreams extends IndexRepositoryStreams {
    private[this] val writeFlow = Flow[IndexDiff]
      .via(Serialization.toBytes())
      .via(Compression.gzip)

    private[this] val readFlow = Flow[ByteString]
      .via(Compression.gunzip())
      .via(Serialization.fromBytes[IndexDiff]())

    def write[Key](repository: IndexRepository[Key]): Flow[(Key, IndexDiff), (Key, IndexDiff), NotUsed] = {
      Flow[(Key, IndexDiff)]
        .flatMapMerge(3, { case (key, value) ⇒
          Source.single(value)
            .alsoTo(writeFlow.to(repository.write(key)))
            .map((key, _))
        })
    }

    def read[Key](repository: IndexRepository[Key]): Flow[Key, (Key, IndexDiff), NotUsed] = {
      Flow[Key]
        .flatMapMerge(3, key ⇒ repository.read(key).via(readFlow).map((key, _)))
    }
  }

  def apply(): IndexRepositoryStreams = new DefaultIndexRepositoryStreams
}
