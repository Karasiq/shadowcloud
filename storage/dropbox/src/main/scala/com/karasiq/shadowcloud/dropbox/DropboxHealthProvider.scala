package com.karasiq.shadowcloud.dropbox

import scala.concurrent.ExecutionContext

import com.karasiq.dropbox.client.DropboxClient
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider

object DropboxHealthProvider {
  def apply(dropboxClient: DropboxClient)(implicit ec: ExecutionContext): DropboxHealthProvider = {
    new DropboxHealthProvider(dropboxClient)
  }
}

class DropboxHealthProvider(dropboxClient: DropboxClient)(implicit ec: ExecutionContext) extends StorageHealthProvider {
  def health = dropboxClient.spaceUsage().map { spaceUsage ⇒
    val used  = spaceUsage.getUsed
    val total = spaceUsage.getAllocation.getIndividualValue.getAllocated
    StorageHealth.normalized(total - used, total, used)
  }
}
