package com.karasiq.shadowcloud.storage

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps.Quota

trait StorageHealthProvider {
  def health: Future[StorageHealth]
}

object StorageHealthProvider {
  val unlimited: StorageHealthProvider = new StorageHealthProvider {
    override def health: Future[StorageHealth] = Future.successful(StorageHealth.unlimited)
  }

  def applyQuota(hp: StorageHealthProvider, quota: Quota)(implicit ec: ExecutionContext): StorageHealthProvider = hp match {
    case qp: QuotedStorageHealthProvider ⇒
      qp.copy(quota = quota)

    case _ ⇒
      QuotedStorageHealthProvider(hp, quota)
  }
  
  private final case class QuotedStorageHealthProvider(underlying: StorageHealthProvider, quota: Quota)(implicit ec: ExecutionContext) extends StorageHealthProvider {
    def health: Future[StorageHealth] = underlying.health.map { sh ⇒
      val newTotalSpace = Quota.limitTotalSpace(quota, sh.totalSpace)
      StorageHealth.normalized(sh.writableSpace, newTotalSpace, sh.usedSpace, sh.online)
    }
  }
}