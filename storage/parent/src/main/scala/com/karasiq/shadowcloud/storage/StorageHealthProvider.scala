package com.karasiq.shadowcloud.storage

import scala.concurrent.Future
import scala.language.postfixOps

import com.karasiq.shadowcloud.model.utils.StorageHealth

trait StorageHealthProvider {
  def health: Future[StorageHealth]
}

object StorageHealthProvider {
  val unlimited: StorageHealthProvider = new StorageHealthProvider {
    override def health: Future[StorageHealth] = Future.successful(StorageHealth.unlimited)
  }
}