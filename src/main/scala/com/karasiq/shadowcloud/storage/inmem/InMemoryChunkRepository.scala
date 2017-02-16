package com.karasiq.shadowcloud.storage.inmem

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.BaseChunkRepository
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

/**
  * Stores chunks in [[scala.collection.concurrent.TrieMap TrieMap]]
  */
private[storage] final class InMemoryChunkRepository extends BaseChunkRepository {
  private[this] val _chunks = TrieMap.empty[String, ByteString]

  def chunks: Source[String, _] = {
    Source.fromIterator(() ⇒ _chunks.keysIterator)
  }

  def read(key: String): Source[ByteString, _] = {
    Source.fromFuture(Future.fromTry(Try(_chunks(key))))
  }

  def write(key: String): Sink[ByteString, _] = {
    ByteStringConcat().to(Sink.foreach { data ⇒
      if (_chunks.putIfAbsent(key, data).nonEmpty) {
        throw new IllegalArgumentException(s"Chunk already exists: $key")
      }
    })
  }
}
