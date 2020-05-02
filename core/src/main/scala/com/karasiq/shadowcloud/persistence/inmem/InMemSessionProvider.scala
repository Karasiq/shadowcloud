package com.karasiq.shadowcloud.persistence.inmem

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.Try

import akka.Done
import akka.util.ByteString

import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.providers.SessionProvider

private[shadowcloud] class InMemSessionProvider extends SessionProvider {
  private[this] val dataMap = TrieMap.empty[(StorageId, String), ByteString]

  def getSessions(storageId: StorageId) = {
    Future.fromTry(Try(dataMap.keys.filter(_._1 == storageId).map(_._2).toSet))
  }

  def storeSession(storageId: StorageId, key: String, data: ByteString) = {
    dataMap += ((storageId, key) → data)
    Future.successful(Done)
  }

  def loadSession(storageId: StorageId, key: String) = {
    Future.fromTry(Try(dataMap((storageId, key))))
  }

  def listSessions(storageId: StorageId): Future[Seq[String]] =
    Future.successful(dataMap.keys.filter(_._1 == storageId).map(_._2).toVector)

  def dropSessions(storageId: StorageId) = Future.fromTry(Try {
    dataMap.keys
      .filter(_._1 == storageId)
      .foreach(dataMap -= _)

    Done
  })

  def dropSession(storageId: StorageId, key: String) = {
    dataMap -= (storageId → key)
    Future.successful(Done)
  }
}
