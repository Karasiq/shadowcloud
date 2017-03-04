package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import org.bouncycastle.jce.provider.BouncyCastleProvider

import scala.language.postfixOps

private[bouncycastle] object BCUtils {
  val provider = new BouncyCastleProvider
}
