package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.math.BigInteger

import scala.util.control.NonFatal

import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.CryptoMethod

private[bouncycastle] object RSAUtils {
  object defaults {
    val publicExponent = 65537L
    val certainty = 12
  }

  def getPublicExponent(method: CryptoMethod): Long = {
    try {
      val config = ConfigProps.toConfig(method.config)
      config.getLong("rsa.public-exponent")
    } catch { case NonFatal(_) ⇒
      defaults.publicExponent
    }
  }

  def createKeyGenerator(keySize: Int, publicExponent: Long = defaults.publicExponent): AsymmetricCipherKeyPairGenerator = {
    val generator = new RSAKeyPairGenerator
    val secureRandom =  BCUtils.createSecureRandom()
    val rsaParameters = new RSAKeyGenerationParameters(BigInteger.valueOf(publicExponent), secureRandom, keySize, defaults.certainty)
    generator.init(rsaParameters)
    generator
  }
}
