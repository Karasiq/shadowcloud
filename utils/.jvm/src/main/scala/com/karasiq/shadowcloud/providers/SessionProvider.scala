package com.karasiq.shadowcloud.providers

import scala.concurrent.Future

import akka.Done
import akka.util.ByteString

import com.karasiq.shadowcloud.model.StorageId

trait SessionProvider {
  def getSessions(storageId: StorageId): Future[Set[String]]
  def storeSession(storageId: StorageId, key: String, data: ByteString): Future[Done]
  def loadSession(storageId: StorageId, key: String): Future[ByteString]
  def dropSessions(storageId: StorageId): Future[Done]
  def dropSession(storageId: StorageId, key: String): Future[Done]
}
