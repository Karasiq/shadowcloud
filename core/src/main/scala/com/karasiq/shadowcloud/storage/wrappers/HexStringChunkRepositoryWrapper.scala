package com.karasiq.shadowcloud.storage.wrappers

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.ChunkRepository.BaseChunkRepository
import com.karasiq.shadowcloud.utils.HexString

import scala.concurrent.Future
import scala.language.postfixOps

private[storage] final class HexStringChunkRepositoryWrapper(underlying: BaseChunkRepository) extends ByteStringChunkRepository {
  def chunks: Source[ByteString, NotUsed] = underlying.chunks.map(HexString.decode)
  def write(hash: ByteString): Sink[ByteString, Future[IOResult]] = underlying.write(HexString.encode(hash))
  def read(hash: ByteString): Source[ByteString, Future[IOResult]] = underlying.read(HexString.encode(hash))
}
