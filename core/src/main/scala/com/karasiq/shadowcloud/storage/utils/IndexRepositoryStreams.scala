package com.karasiq.shadowcloud.storage.utils

import akka.NotUsed
import akka.stream.scaladsl.{Compression, Flow}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.serialization.Serialization
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
  private object Flows {
    val write = Flow[IndexDiff].via(Serialization.toBytes())
    val read = Flow[ByteString].via(ByteStringConcat()).via(Serialization.fromBytes[IndexDiff]())
    val gzipWrite = write.via(Compression.gzip)
    val gzipRead = Flow[ByteString].via(Compression.gunzip()).via(read)
  }

  def create(breadth: Int, writeFlow: Flow[IndexDiff, ByteString, _],
             readFlow: Flow[ByteString, IndexDiff, _])(implicit ec: ExecutionContext): IndexRepositoryStreams = {
    new DefaultIndexRepositoryStreams(breadth, writeFlow, readFlow)
  }

  def default(implicit ec: ExecutionContext): IndexRepositoryStreams = {
    create(3, Flows.write, Flows.read)
  }
  
  def gzipped(implicit ec: ExecutionContext): IndexRepositoryStreams = {
    create(3, Flows.gzipWrite, Flows.gzipRead)
  }
}
