package com.karasiq.shadowcloud.mailrucloud

import akka.actor.ActorContext

import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

object MailRuCloudStoragePlugin {
  def apply(): MailRuCloudStoragePlugin = {
    new MailRuCloudStoragePlugin()
  }
}

class MailRuCloudStoragePlugin extends StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext) = {
    context.actorOf(MailRuCloudProxyActor.props(storageId, props), "mailrucloud-proxy")
  }
}
