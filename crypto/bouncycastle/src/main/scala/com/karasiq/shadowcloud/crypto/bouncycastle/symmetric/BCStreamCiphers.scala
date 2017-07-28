package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import java.security.NoSuchAlgorithmException

import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.{ChaChaEngine, Salsa20Engine, XSalsa20Engine}

private[bouncycastle] object BCStreamCiphers {
  val algorithms = Set("Salsa20", "XSalsa20", "ChaCha20")

  def isStreamAlgorithm(algorithm: String): Boolean = {
    algorithms.contains(algorithm)
  }

  def getNonceSize(algorithm: String): Int = algorithm match {
    case "Salsa20" | "ChaCha20" ⇒
      8

    case "XSalsa20" ⇒
      24

    case _ ⇒
      throw new NoSuchAlgorithmException(algorithm)
  }

  def createStreamCipher(algorithm: String): StreamCipher = algorithm match {
    case "Salsa20" ⇒
      new Salsa20Engine()

    case "XSalsa20" ⇒
      new XSalsa20Engine()

    case "ChaCha20" ⇒
      new ChaChaEngine()

    case _ ⇒
      throw new NoSuchAlgorithmException(algorithm)
  }
}
