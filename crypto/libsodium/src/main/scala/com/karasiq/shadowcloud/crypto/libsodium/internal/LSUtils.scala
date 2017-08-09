package com.karasiq.shadowcloud.crypto.libsodium.internal

import scala.language.postfixOps
import scala.util.Try

import org.abstractj.kalium.NaCl

private[libsodium] object LSUtils {
  type LSRandom = org.abstractj.kalium.crypto.Random

  private[this] lazy val sodiumInstance = Try {
    val sodium = NaCl.sodium()
    require(sodium.sodium_init() != -1)
    sodium
  }

  lazy val isLibraryAvailable: Boolean = sodiumInstance.isSuccess
  lazy val isAesAvailable: Boolean = isLibraryAvailable && sodiumInstance.get.crypto_aead_aes256gcm_is_available() == 1

  def createSecureRandom(): LSRandom = {
    new LSRandom
  }
}
