package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps

trait CryptoMethod {
  def algorithm: String
  def stream: Boolean
  def provider: String
  def config: SerializedProps
}

object CryptoMethod {
  def isNoOpMethod(m: CryptoMethod): Boolean = {
    m.algorithm.isEmpty
  }
}