package com.karasiq.shadowcloud.storage.utils

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Compression, Flow}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.serialization.{SerializationModule, SerializationModules, StreamSerialization}
import com.karasiq.shadowcloud.storage.Repository
import com.karasiq.shadowcloud.storage.internal.DefaultIndexRepositoryStreams
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

private[shadowcloud] trait IndexRepositoryStreams {
  def write[Key](repository: Repository[Key]): Flow[(Key, IndexDiff), IndexIOResult[Key], NotUsed]
  def read[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed]
  def delete[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed]
}

// TODO: Encryption, signatures
private[shadowcloud] object IndexRepositoryStreams {
  def create(breadth: Int, writeFlow: Flow[IndexDiff, ByteString, _],
             readFlow: Flow[ByteString, IndexDiff, _])(implicit ec: ExecutionContext): IndexRepositoryStreams = {
    new DefaultIndexRepositoryStreams(breadth, writeFlow, readFlow)
  }

  def default(implicit as: ActorSystem): IndexRepositoryStreams = {
    import as.dispatcher
    val flows = Flows(SerializationModules.fromActorSystem(as))
    create(3, flows.write, flows.read)
  }
  
  def gzipped(implicit as: ActorSystem): IndexRepositoryStreams = {
    import as.dispatcher
    val flows = Flows(SerializationModules.fromActorSystem(as))
    create(3, flows.gzipWrite, flows.gzipRead)
  }

  private[this] final case class Flows(private val module: SerializationModule) extends AnyVal {
    type WFlow = Flow[IndexDiff, ByteString, NotUsed]
    type RFlow = Flow[ByteString, IndexDiff, NotUsed]
    def write: WFlow = Flow[IndexDiff].via(StreamSerialization(module).toBytes)
    def read: RFlow = Flow[ByteString].via(ByteStringConcat()).via(StreamSerialization(module).fromBytes[IndexDiff])
    def gzipWrite: WFlow = write.via(Compression.gzip)
    def gzipRead: RFlow = Flow[ByteString].via(Compression.gunzip()).via(read)
  }
}
