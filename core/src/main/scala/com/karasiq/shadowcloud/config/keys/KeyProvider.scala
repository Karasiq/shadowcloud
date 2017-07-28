package com.karasiq.shadowcloud.config.keys

import scala.concurrent.Future

trait KeyProvider {
  def getKeyChain(): Future[KeyChain]
}
