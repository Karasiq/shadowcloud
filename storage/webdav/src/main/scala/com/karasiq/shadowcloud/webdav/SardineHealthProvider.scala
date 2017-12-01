package com.karasiq.shadowcloud.webdav

import scala.concurrent.{ExecutionContext, Future}

import com.github.sardine.Sardine

import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps

object SardineHealthProvider {
  def apply(props: StorageProps, sardine: Sardine)(implicit ec: ExecutionContext): SardineHealthProvider = {
    new SardineHealthProvider(props, sardine)
  }
}

class SardineHealthProvider(props: StorageProps, sardine: Sardine)(implicit ec: ExecutionContext) extends StorageHealthProvider {
  def health = Future {
    val quota = sardine.getQuota(props.address.uri.getOrElse(throw new IllegalArgumentException("No WebDav URL")).toString)
    StorageHealth.normalized(quota.getQuotaAvailableBytes, quota.getQuotaAvailableBytes + quota.getQuotaUsedBytes, quota.getQuotaUsedBytes)
  }
}
