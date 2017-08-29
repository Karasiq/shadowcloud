package com.karasiq.shadowcloud.model.crypto

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.SCEntity

trait CryptoParameters extends SCEntity with HasEmpty {
  def method: CryptoMethod
}
