package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.BaseIndexRepository

import scala.language.postfixOps

private[storage] final class NumericIndexRepositoryWrapper(underlying: BaseIndexRepository) extends NumericIndexRepository {
  def keys: Source[Long, _] = underlying.keys.map(_.toLong)
  def read(key: Long): Source[ByteString, _] = underlying.read(key.toString)
  def write(key: Long): Sink[ByteString, _] = underlying.write(key.toString)
}
