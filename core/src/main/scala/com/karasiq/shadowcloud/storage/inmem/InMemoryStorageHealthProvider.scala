package com.karasiq.shadowcloud.storage.inmem

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}
import com.karasiq.shadowcloud.storage.props.StorageProps

private[storage] final class InMemoryStorageHealthProvider(maps: Seq[mutable.Map[_, ByteString]],
                                                           quota: StorageProps.Quota = StorageProps.Quota.empty)
  extends StorageHealthProvider {

  def health: Future[StorageHealth] = {
    val total = quota.getLimitedSpace(sys.runtime.totalMemory())
    val used = maps.iterator
      .flatMap(_.valuesIterator)
      .map(_.length)
      .foldLeft(0L)(_ + _)

    Future.successful(StorageHealth(math.max(0L, total - used), total, used))
  }
}
