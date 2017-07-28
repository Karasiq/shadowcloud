package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import scala.language.postfixOps

import org.bouncycastle.jce.provider.BouncyCastleProvider

private[bouncycastle] object BCUtils {
  val provider = new BouncyCastleProvider

  def algorithmAndMode(algorithm: String): (String, String) = algorithm.split("/", 2) match {
    case Array(algorithm, mode) ⇒
      (algorithm, mode)

    case Array(algorithm) ⇒
      (algorithm, "CBC")

    case _ ⇒
      throw new IllegalArgumentException
  }
}
