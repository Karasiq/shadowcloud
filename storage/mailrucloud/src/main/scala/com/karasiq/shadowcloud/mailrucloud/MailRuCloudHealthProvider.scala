package com.karasiq.shadowcloud.mailrucloud

import scala.concurrent.ExecutionContext

import com.karasiq.mailrucloud.api.MailCloudClient
import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Session}
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider

object MailRuCloudHealthProvider {
  def apply(client: MailCloudClient)(implicit ec: ExecutionContext, session: Session, token: CsrfToken): MailRuCloudHealthProvider = {
    new MailRuCloudHealthProvider(client)
  }
}

class MailRuCloudHealthProvider(client: MailCloudClient)(implicit ec: ExecutionContext, session: Session, token: CsrfToken) extends StorageHealthProvider {
  def health = {
    client.space.map { space ⇒
      StorageHealth.normalized(space.total - space.used, space.total, space.used)
    }
  }
}
