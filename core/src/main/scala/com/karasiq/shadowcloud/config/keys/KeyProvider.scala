package com.karasiq.shadowcloud.config.keys

import scala.concurrent.Future

// TODO: Import/export keys function
trait KeyProvider {
  def getKeyChain(): Future[KeyChain]
}
