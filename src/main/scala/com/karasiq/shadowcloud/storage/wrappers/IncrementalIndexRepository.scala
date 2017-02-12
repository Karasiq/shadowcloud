package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.{BaseIndexRepository, IndexRepository}

import scala.language.postfixOps

class IncrementalIndexRepository(underlying: BaseIndexRepository) extends IndexRepository[Long] {
  def keys: Source[Long, _] = underlying.keys.map(_.toLong)
  def read(key: Long): Source[ByteString, _] = underlying.read(key.toString)
  def write(key: Long): Sink[ByteString, _] = underlying.write(key.toString)
  def keysAfter(id: Long): Source[Long, _] = keys.filter(_ > id)
}
