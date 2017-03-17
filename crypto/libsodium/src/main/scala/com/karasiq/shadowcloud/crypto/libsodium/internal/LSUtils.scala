package com.karasiq.shadowcloud.crypto.libsodium.internal

import org.abstractj.kalium.NaCl

import scala.language.postfixOps

private[libsodium] object LSUtils {
  val libraryAvailable: Boolean = {
    try { NaCl.sodium() ne null } catch { case _: UnsatisfiedLinkError ⇒ false }
  }

  val aes256GcmAvailable: Boolean = {
    libraryAvailable && NaCl.sodium().crypto_aead_aes256gcm_is_available() == 1
  }
}
