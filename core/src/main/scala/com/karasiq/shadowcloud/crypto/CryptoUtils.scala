package com.karasiq.shadowcloud.crypto

import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.language.postfixOps

private[crypto] object CryptoUtils {
  val provider = new BouncyCastleProvider
}
