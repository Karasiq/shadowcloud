package com.karasiq.shadowcloud.crypto.libsodium.internal

import scala.language.postfixOps

import org.abstractj.kalium.NaCl

private[libsodium] object LSUtils {
  type LSRandom = org.abstractj.kalium.crypto.Random

  lazy val libraryAvailable: Boolean = {
    try { NaCl.sodium() ne null } catch { case _: UnsatisfiedLinkError ⇒ false }
  }

  lazy val aes256GcmAvailable: Boolean = {
    libraryAvailable && NaCl.sodium().crypto_aead_aes256gcm_is_available() == 1
  }

  def createSecureRandom(): LSRandom = {
    new LSRandom
  }
}
