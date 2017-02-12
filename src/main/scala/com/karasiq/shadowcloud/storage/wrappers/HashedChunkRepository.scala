package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.{BaseChunkRepository, ChunkRepository}
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

class HashedChunkRepository(underlying: BaseChunkRepository) extends ChunkRepository[ByteString] {
  def chunks: Source[ByteString, _] = underlying.chunks.map(Utils.parseHexString)
  def write(hash: ByteString): Sink[ByteString, _] = underlying.write(Utils.toHexString(hash))
  def read(hash: ByteString): Source[ByteString, _] = underlying.read(Utils.toHexString(hash))
}
