package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.storage.{BaseRepository, SeqRepository}

private[storage] final class LongSeqRepositoryWrapper(underlying: BaseRepository) extends SeqRepository[Long] {
  def keys: Source[Long, Result] = underlying.keys.map(_.toLong)
  def read(key: Long): Source[Data, Result] = underlying.read(key.toString)
  def write(key: Long): Sink[Data, Result] = underlying.write(key.toString)
}
