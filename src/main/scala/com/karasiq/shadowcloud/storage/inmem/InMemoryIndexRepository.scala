package com.karasiq.shadowcloud.storage.inmem

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.BaseIndexRepository
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

/**
  * Stores index in [[scala.collection.concurrent.TrieMap TrieMap]]
  */
private[storage] final class InMemoryIndexRepository extends BaseIndexRepository {
  private[this] val _diffs = TrieMap.empty[String, ByteString]

  def keys: Source[String, _] = {
    Source.fromIterator(() ⇒ _diffs.keysIterator)
  }

  def read(key: String): Source[ByteString, _] = {
    Source.fromFuture(Future.fromTry(Try(_diffs(key))))
  }

  def write(key: String): Sink[ByteString, _] = {
    ByteStringConcat().to(Sink.foreach { data ⇒
      if (_diffs.putIfAbsent(key, data).nonEmpty) {
        throw new IllegalArgumentException(s"Index entry already exists: $key")
      }
    })
  }
}
