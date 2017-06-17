package com.karasiq.shadowcloud.config.keys

import java.util.UUID

import scala.concurrent.Future

trait KeyProvider {
  def forEncryption(): Future[KeySet]
  def forDecryption(keyId: UUID): Future[KeySet]
}
