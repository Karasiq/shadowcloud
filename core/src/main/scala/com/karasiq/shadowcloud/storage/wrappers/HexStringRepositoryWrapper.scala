package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.Repository.BaseRepository
import com.karasiq.shadowcloud.utils.HexString

private[storage] final class HexStringRepositoryWrapper(underlying: BaseRepository) extends ByteStringRepository {
  def keys: Source[ByteString, Result] = underlying.keys.map(HexString.decode)
  def write(hash: ByteString): Sink[Data, Result] = underlying.write(HexString.encode(hash))
  def read(hash: ByteString): Source[Data, Result] = underlying.read(HexString.encode(hash))
}
