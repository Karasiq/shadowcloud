package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.language.postfixOps

trait IndexRepository[Key] {
  def keys: Source[Key, _]
  def read(key: Key): Source[ByteString, _]
  def write(key: Key): Sink[ByteString, _]
}

trait IncrementalIndexRepository extends IndexRepository[Long] {
  def keysAfter(time: Long): Source[Long, _] = {
    keys.filter(_ > time)
  }
}