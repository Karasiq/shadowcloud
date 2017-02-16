package com.karasiq.shadowcloud.storage.inmem

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.IndexRepository
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Stores index in [[scala.collection.concurrent.TrieMap TrieMap]]
  */
private[storage] class InMemoryIndexRepository[Key] extends IndexRepository[Key] {
  private[this] val underlying = new TrieMapStreams[Key, ByteString](TrieMap.empty, _.length)

  def keys: Source[Key, NotUsed] = {
    underlying.keys
  }

  def read(key: Key): Source[ByteString, Future[IOResult]] = {
    underlying.read(key)
  }

  def write(key: Key): Sink[ByteString, Future[IOResult]] = {
    ByteStringConcat()
      .toMat(underlying.write(key))(Keep.right)
  }
}
