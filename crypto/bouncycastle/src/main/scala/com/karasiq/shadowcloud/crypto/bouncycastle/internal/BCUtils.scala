package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import scala.language.postfixOps

import org.bouncycastle.jce.provider.BouncyCastleProvider

private[bouncycastle] object BCUtils {
  val provider = new BouncyCastleProvider
}
