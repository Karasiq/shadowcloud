package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.wrappers.IncrementalIndexRepository

import scala.language.postfixOps

trait IndexRepository[Key] {
  def keys: Source[Key, _]
  def read(key: Key): Source[ByteString, _]
  def write(key: Key): Sink[ByteString, _]
}

trait BaseIndexRepository extends IndexRepository[String]

object IndexRepository {
  def incremental(underlying: BaseIndexRepository): IncrementalIndexRepository = {
    new IncrementalIndexRepository(underlying)
  }
}