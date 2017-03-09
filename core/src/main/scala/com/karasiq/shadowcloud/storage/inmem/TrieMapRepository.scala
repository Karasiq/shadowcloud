package com.karasiq.shadowcloud.storage.inmem

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.Repository
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Success

/**
  * Stores data in [[scala.collection.concurrent.TrieMap TrieMap]]
  */
private[storage] class TrieMapRepository[Key](storage: TrieMap[Key, ByteString]) extends Repository[Key] {
  private[this] val underlying = new TrieMapStreams[Key, ByteString](storage, _.length)

  def keys: Source[Key, Result] = {
    underlying.keys
      .mapMaterializedValue(_ ⇒ Future.successful(IOResult(0, Success(Done))))
  }

  def read(key: Key): Source[Data, Result] = {
    underlying.read(key)
  }

  def write(key: Key): Sink[Data, Result] = {
    ByteStringConcat()
      .toMat(underlying.write(key))(Keep.right)
  }
}
