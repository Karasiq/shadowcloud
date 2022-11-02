package com.karasiq.shadowcloud.crypto.libsodium.internal

import org.abstractj.kalium.NaCl

import scala.util.control.{Exception ⇒ ExcControl}

private[libsodium] object LSUtils {
  type LSRandom = org.abstractj.kalium.crypto.Random

  private[this] lazy val sodiumInstance = ExcControl.allCatch.opt {
    val sodium = NaCl.sodium()
    require(sodium.sodium_init() != -1)
    sodium
  }

  lazy val isLibraryAvailable: Boolean = sodiumInstance.isDefined
  lazy val isAesAvailable: Boolean     = isLibraryAvailable && sodiumInstance.forall(_.crypto_aead_aes256gcm_is_available() == 1)

  def createSecureRandom(): LSRandom = {
    new LSRandom
  }
}
