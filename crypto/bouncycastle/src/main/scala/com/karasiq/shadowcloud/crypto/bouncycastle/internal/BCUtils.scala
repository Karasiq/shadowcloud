package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.security.SecureRandom

import scala.language.postfixOps

import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}
import org.bouncycastle.jce.provider.BouncyCastleProvider

import com.karasiq.shadowcloud.crypto.EncryptionParameters

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

  def createSecureRandom(): SecureRandom = {
    // new SecureRandom()
    // SecureRandom.getInstanceStrong
    SecureRandom.getInstance("DEFAULT", provider) // org.bouncycastle.jcajce.provider.drbg.DRBG.Default
  }

  def toParametersWithIV(parameters: EncryptionParameters): ParametersWithIV = {
    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    new ParametersWithIV(
      new KeyParameter(symmetricParameters.key.toArray),
      symmetricParameters.nonce.toArray
    )
  }
}
