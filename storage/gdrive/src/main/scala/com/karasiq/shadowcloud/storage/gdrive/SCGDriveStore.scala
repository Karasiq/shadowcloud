package com.karasiq.shadowcloud.storage.gdrive

import java.{io, util}

import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.util.ByteString
import com.google.api.client.util.IOUtils
import com.google.api.client.util.store.{AbstractDataStore, AbstractDataStoreFactory, DataStore}

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.model.StorageId

object SCGDriveStore {
  def apply(storageId: StorageId, userId: String)(implicit sc: ShadowCloudExtension, ec: ExecutionContext): SCGDriveStore = {
    new SCGDriveStore(storageId, userId)
  }
}

class SCGDriveStore(storageId: StorageId, userId: String)(implicit sc: ShadowCloudExtension, ec: ExecutionContext) extends AbstractDataStoreFactory {
  def createDataStore[V <: io.Serializable](id: StorageId): DataStore[V] = {
    new SCStore[AnyRef with Serializable](id).asInstanceOf[DataStore[V]]
  }

  private final class SCStore[V <: AnyRef with Serializable](id: String) extends AbstractDataStore[V](SCGDriveStore.this, id) {
    private[this] val prefix = "gdrive_" + userId + "_" + id + "_"
    private[this] val timeout = 10 seconds

    def set(key: String, value: V): DataStore[V] = {
      val future = sc.sessions.provider.storeSession(storageId, prefix + key, serialize(value))
      Await.ready(future, timeout)
      this
    }

    def keySet(): util.Set[String] = {
      Await.result(unprefixedKeys(), timeout).asJava
    }

    def values(): util.Collection[V] = {
      val future = prefixedKeys()
        .flatMap(keys ⇒ Future.sequence(keys.toVector.map(key ⇒ sc.sessions.provider.loadSession(storageId, key))))
        .map(_.map(deserialize))

      Await.result(future, timeout).asJavaCollection
    }

    def clear(): DataStore[V] = {
      val future = prefixedKeys()
        .flatMap(keys ⇒ Future.sequence(keys.toVector.map(key ⇒ sc.sessions.provider.dropSession(storageId, key))))
      Await.ready(future, timeout)
      this
    }

    def delete(key: String): DataStore[V] = {
      val future = sc.sessions.provider.dropSession(storageId, prefix + key)
      Await.ready(future, timeout)
      this
    }

    def get(key: String): V = {
      val future = sc.sessions.provider.loadSession(storageId, prefix + key)
        .map(deserialize)
        .recover { case _ ⇒ null.asInstanceOf[V] }

      Await.result(future, timeout)
    }

    private[this] def prefixedKeys() = {
      sc.sessions.provider.getSessions(storageId)
        .map(keys ⇒ keys.filter(_.startsWith(prefix)))
    }

    private[this] def unprefixedKeys() = {
      prefixedKeys().map(_.map(_.drop(prefix.length)))
    }

    private[this] def serialize(data: V): ByteString = {
      ByteString.fromArrayUnsafe(IOUtils.serialize(data))
    }

    private[this] def deserialize(data: ByteString) = {
      import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
      IOUtils.deserialize[V](data.toArrayUnsafe)
    }
  }
}
