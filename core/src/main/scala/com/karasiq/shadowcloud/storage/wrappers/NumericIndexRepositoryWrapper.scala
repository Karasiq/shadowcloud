package com.karasiq.shadowcloud.storage.wrappers

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.IndexRepository.BaseIndexRepository

import scala.concurrent.Future
import scala.language.postfixOps

private[storage] final class NumericIndexRepositoryWrapper(underlying: BaseIndexRepository) extends NumericIndexRepository {
  def keys: Source[Long, NotUsed] = underlying.keys.map(_.toLong)
  def read(key: Long): Source[ByteString, Future[IOResult]] = underlying.read(key.toString)
  def write(key: Long): Sink[ByteString, Future[IOResult]] = underlying.write(key.toString)
}
