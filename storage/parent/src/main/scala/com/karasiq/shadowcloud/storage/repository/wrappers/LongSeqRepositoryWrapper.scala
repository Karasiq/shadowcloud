package com.karasiq.shadowcloud.storage.repository.wrappers

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import com.karasiq.shadowcloud.storage.repository.{KeyValueRepository, SeqRepository}

private[repository] final class LongSeqRepositoryWrapper(underlying: KeyValueRepository) extends SeqRepository[Long] {
  def keys: Source[Long, Result] = underlying.keys.map(_.toLong)
  def read(key: Long): Source[Data, Result] = underlying.read(key.toString)
  def write(key: Long): Sink[Data, Result] = underlying.write(key.toString)
  def delete: Sink[Long, Result] = Flow[Long].map(_.toString).toMat(underlying.delete)(Keep.right)
}
