package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.language.postfixOps

trait IndexRepository {
  type IndexKey
  def keys: Source[IndexKey, _]
  def read(key: IndexKey): Source[ByteString, _]
  def write(key: IndexKey): Sink[ByteString, _]
}

trait TimeIndexRepository extends IndexRepository {
  final type IndexKey = Long

  def keysAfter(time: IndexKey): Source[IndexKey, _] = {
    keys.filter(_ > time)
  }
}