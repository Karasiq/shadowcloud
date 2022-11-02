package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.jcajce.provider.asymmetric.util.PrimeCertaintyCalculator

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.model.crypto.{CryptoMethod, EncryptionMethod, SignMethod}

private[bouncycastle] object RSAUtils {
  type KeyGenerator = org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator

  /** @see [[org.bouncycastle.jcajce.provider.asymmetric.rsa.KeyPairGeneratorSpi]]
    */
  object defaults {
    val keySize                      = 2048
    val publicExponent               = BigInt(0x10001)
    def certainty(keySize: Int): Int = PrimeCertaintyCalculator.getDefaultCertainty(keySize)
  }

  private[this] def createKeyGenerator(keySize: Int, publicExponent: BigInt, certainty: Int): KeyGenerator = {
    val generator     = new RSAKeyPairGenerator
    val secureRandom  = BCUtils.createSecureRandom()
    val rsaParameters = new RSAKeyGenerationParameters(publicExponent.bigInteger, secureRandom, keySize, certainty)
    generator.init(rsaParameters)
    generator
  }

  def createKeyGenerator(method: CryptoMethod): KeyGenerator = {
    val options = RSAOptions(method)
    require(options.keySize >= 1024, "RSA key size is too small")
    createKeyGenerator(options.keySize, options.publicExponent, options.certainty)
  }

  private case class RSAOptions(method: CryptoMethod) {
    import com.karasiq.common.configs.ConfigImplicits._
    private[this] val rsaConfig = ConfigProps.toConfig(method.config).getConfigIfExists("rsa")

    val keySize = method match {
      case sm: SignMethod       ⇒ sm.keySize
      case em: EncryptionMethod ⇒ em.keySize
      case _                    ⇒ defaults.keySize
    }
    val publicExponent = rsaConfig.withDefault(defaults.publicExponent, cfg ⇒ BigInt(cfg.getString("public-exponent")))
    val certainty      = rsaConfig.withDefault(defaults.certainty(keySize), _.getInt("certainty"))
  }
}
