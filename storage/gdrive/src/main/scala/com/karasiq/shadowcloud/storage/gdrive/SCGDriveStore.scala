package com.karasiq.shadowcloud.storage.gdrive

import java.{io, util}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import com.google.api.client.util.store.{AbstractDataStore, AbstractDataStoreFactory, DataStore}

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.model.StorageId

object SCGDriveStore {
  def apply(storageId: StorageId, userId: String)(implicit sc: ShadowCloudExtension): SCGDriveStore = {
    new SCGDriveStore(storageId, userId)
  }
}

class SCGDriveStore(storageId: StorageId, userId: String)(implicit sc: ShadowCloudExtension) extends AbstractDataStoreFactory {
  def createDataStore[V <: io.Serializable](id: StorageId) = {
    new SCStore(id)
  }

  private final class SCStore[V](id: String) extends AbstractDataStore[V](SCGDriveStore.this, id) {
    private[this] val prefix = "gdrive_" + userId + "_"

    def set(key: String, value: V): DataStore[V] = {
      val future = sc.sessions.set(storageId, prefix + key, value.asInstanceOf[AnyRef])
      Await.ready(future, 10 seconds)
      this
    }

    def keySet(): util.Set[String] = {
      Await.result(unprefixedKeys(), 10 seconds).asJava
    }

    def values(): util.Collection[V] = {
      val future = prefixedKeys()
        .flatMap(keys ⇒ Future.sequence(keys.toSeq.map(key ⇒ sc.sessions.get[V](storageId, key))))

      Await.result(future, 10 seconds).asJavaCollection
    }

    def clear(): DataStore[V] = {
      val future = prefixedKeys()
        .flatMap(keys ⇒ Future.sequence(keys.toSeq.map(key ⇒ sc.sessions.provider.dropSession(storageId, key))))
      Await.ready(future, 10 seconds)
      this
    }

    def delete(key: String): DataStore[V] = {
      val future = sc.sessions.provider.dropSession(storageId, prefix + key)
      Await.ready(future, 10 seconds)
      this
    }

    def get(key: String): V = {
      val future = sc.sessions.get[V](storageId, prefix + key).recover { case _ ⇒ null.asInstanceOf[V] }
      Await.result(future, 10 seconds)
    }

    private[this] def prefixedKeys() = {
      sc.sessions.provider.getSessions(storageId)
        .map(keys ⇒ keys.filter(_.startsWith(prefix)))
    }

    private[this] def unprefixedKeys() = {
      prefixedKeys().map(_.map(_.drop(prefix.length)))
    }
  }
}
