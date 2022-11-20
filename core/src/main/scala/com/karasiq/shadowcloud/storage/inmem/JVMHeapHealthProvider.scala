package com.karasiq.shadowcloud.storage.inmem

import akka.util.ByteString
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.props.StorageProps.Quota

import scala.concurrent.Future

private[storage] final class JVMHeapHealthProvider(dataIterator: () ⇒ Iterator[ByteString], quota: StorageProps.Quota = StorageProps.Quota.empty)
    extends StorageHealthProvider {

  def health: Future[StorageHealth] = {
    val total = Quota.limitTotalSpace(quota, sys.runtime.maxMemory())
    val used = dataIterator()
      .map(_.length)
      .sum

    Future.successful(StorageHealth.normalized(total - used, total, used))
  }
}
