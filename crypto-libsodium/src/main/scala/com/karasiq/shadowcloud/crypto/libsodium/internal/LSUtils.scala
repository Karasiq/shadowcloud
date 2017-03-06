package com.karasiq.shadowcloud.crypto.libsodium.internal

import org.abstractj.kalium.NaCl

import scala.language.postfixOps

private[libsodium] object LSUtils {
  val libraryLoaded: Boolean = {
    try { NaCl.sodium() ne null } catch { case _: UnsatisfiedLinkError ⇒ false }
  }
}
