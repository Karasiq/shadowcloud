package com.karasiq.shadowcloud.storage.inmem

import scala.collection.concurrent.{Map ⇒ CMap}
import scala.language.postfixOps

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.storage.repository.Repository
import com.karasiq.shadowcloud.streams.utils.ByteStreams

/**
  * Stores data in [[scala.collection.concurrent.TrieMap TrieMap]]
  */
private[storage] final class ConcurrentMapRepository[Key](storage: CMap[Key, ByteString]) extends Repository[Key] {
  private[this] val underlying = new ConcurrentMapStreams[Key, ByteString](storage, _.length)

  def keys: Source[Key, Result] = {
    underlying.keys
  }

  def read(key: Key): Source[Data, Result] = {
    underlying.read(key)
  }

  def write(key: Key): Sink[Data, Result] = {
    Flow[ByteString]
      .via(ByteStreams.concat)
      .toMat(underlying.write(key))(Keep.right)
  }

  def delete(key: Key): Result = {
    underlying.delete(key)
  }
}
