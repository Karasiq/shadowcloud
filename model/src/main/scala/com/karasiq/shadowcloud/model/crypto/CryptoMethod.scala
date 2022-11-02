package com.karasiq.shadowcloud.model.crypto

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.SCEntity

trait CryptoMethod extends SCEntity {
  def algorithm: String
  def provider: String
  def config: SerializedProps
}

object CryptoMethod {
  def isNoOpMethod(m: CryptoMethod): Boolean = {
    m.algorithm.isEmpty
  }
}
