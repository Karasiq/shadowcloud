package com.karasiq.shadowcloud.webdav

import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps

object SardineHealthProvider {
  def apply(props: StorageProps)(implicit ec: ExecutionContext): SardineHealthProvider = {
    new SardineHealthProvider(props)
  }
}

class SardineHealthProvider(props: StorageProps)(implicit ec: ExecutionContext) extends StorageHealthProvider {
  private[this] val sardine = SardineRepository.createSardine(props)

  def health = Future {
    val quota = sardine.getQuota(props.address.uri.getOrElse(throw new IllegalArgumentException("No WebDav URL")).toString)
    StorageHealth.normalized(quota.getQuotaAvailableBytes, quota.getQuotaAvailableBytes + quota.getQuotaUsedBytes, quota.getQuotaUsedBytes)
  }
}
