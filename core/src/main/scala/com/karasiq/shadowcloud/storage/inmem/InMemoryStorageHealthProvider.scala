package com.karasiq.shadowcloud.storage.inmem

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

private[storage] final class InMemoryStorageHealthProvider(maps: Seq[mutable.Map[_, ByteString]]) extends StorageHealthProvider {
  def health: Future[StorageHealth] = {
    val total = sys.runtime.totalMemory()
    val used = maps.iterator.flatMap(_.valuesIterator.map(_.length.toLong)).sum
    Future.successful(StorageHealth(math.max(0L, total - used), total, used))
  }
}
