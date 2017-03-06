package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps

import scala.language.postfixOps

trait CryptoMethod {
  def algorithm: String
  def stream: Boolean
  def provider: String
  def config: SerializedProps
}
