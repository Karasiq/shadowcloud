package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import java.security.SecureRandom

import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.ConfigValueFactory
import org.bouncycastle.crypto.{AsymmetricCipherKeyPairGenerator, BufferedBlockCipher, Digest, KeyEncoder}
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.engines.IESEngine
import org.bouncycastle.crypto.generators.{EphemeralKeyPairGenerator, KDF2BytesGenerator}
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.{AsymmetricKeyParameter, ECPublicKeyParameters, IESParameters, IESWithCipherParameters}
import org.bouncycastle.crypto.parsers.ECIESPublicKeyParser

import com.karasiq.shadowcloud.config.{ConfigProps, CryptoProps}
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{AsymmetricEncryptionParameters, EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.ECUtils
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.BCECKeys
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.BCBlockCiphers
import com.karasiq.shadowcloud.utils.HexString

private[bouncycastle] object ECIESCipherModule extends ConfigImplicits {
  private[this] val secureRandom = new SecureRandom()

  def apply(method: EncryptionMethod = EncryptionMethod("ECIES")): ECIESCipherModule = {
    new ECIESCipherModule(method)
  }

  private def getBlockCipherMethod(config: Config): Option[EncryptionMethod] = {
    config
      .optional(_.getConfig("ies-block-cipher"))
      .map(CryptoProps.encryption)
      .orElse(config.optional(_.getString("ies-block-cipher")).map(EncryptionMethod(_)))
      .filter(_.algorithm.nonEmpty)
  }

  private def getIesParameters(method: EncryptionMethod): IESParameters = {
    val config = ConfigProps.toConfig(method.config)

    def getByteArrayOrNull(name: String): Array[Byte] = {
      config.withDefault(null, cfg ⇒ HexString.decode(cfg.getString(name)).toArray)
    }

    val derivation = getByteArrayOrNull("ies-derivation")
    val encoding = getByteArrayOrNull("ies-encoding")
    val macKeySize = config.withDefault(64, _.getInt("ies-mac-key-size"))

    getBlockCipherMethod(config) match {
      case Some(bcMethod) ⇒
        new IESWithCipherParameters(derivation, encoding, macKeySize, bcMethod.keySize)

      case None ⇒
        // Streaming mode
        new IESParameters(derivation, encoding, macKeySize)
    }
  }

  private def addIesParameters(parameters: AsymmetricEncryptionParameters): AsymmetricEncryptionParameters = {
    def randomBytes(size: Int): String = {
      val bytes = new Array[Byte](size)
      secureRandom.nextBytes(bytes)
      HexString.encode(ByteString(bytes))
    }
    val newConfig = ConfigProps.toConfig(parameters.method.config)
      .withValue("ies-derivation", ConfigValueFactory.fromAnyRef(randomBytes(8)))
      .withValue("ies-encoding", ConfigValueFactory.fromAnyRef(randomBytes(8)))
    parameters.copy(method = parameters.method.copy(config = ConfigProps.fromConfig(newConfig)))
  }

  private def createIesEngine(method: EncryptionMethod): IESEngine = {
    def createDigest(config: Config): Digest = {
      val digestAlg = config.withDefault("SHA-512", _.getString("ies-digest"))
      BCDigests.createDigest(digestAlg)
    }

    def createBlockCipher(config: Config): Option[BufferedBlockCipher] = {
      val bcMethod = getBlockCipherMethod(config)
      bcMethod.map(m ⇒ BCBlockCiphers.buffered(BCBlockCiphers.createBlockCipher(m.algorithm, m.keySize)))
    }

    val config = ConfigProps.toConfig(method.config)
    val agreement = new ECDHBasicAgreement()
    val kdfGenerator = new KDF2BytesGenerator(createDigest(config))
    val mac = new HMac(createDigest(config))
    createBlockCipher(config) match {
      case Some(blockCipher) ⇒
        new IESEngine(agreement, kdfGenerator, mac, blockCipher)

      case None ⇒
        // Streaming mode
        new IESEngine(agreement, kdfGenerator, mac)
    }
  }

  //noinspection ConvertExpressionToSAM
  private def createEphKeyGenerator(keyPairGenerator: AsymmetricCipherKeyPairGenerator): EphemeralKeyPairGenerator = {
    new EphemeralKeyPairGenerator(keyPairGenerator, new KeyEncoder {
      def getEncoded(keyParameter: AsymmetricKeyParameter): Array[Byte] = {
        keyParameter.asInstanceOf[ECPublicKeyParameters].getQ.getEncoded(false)
      }
    })
  }
}

private[bouncycastle] final class ECIESCipherModule(val method: EncryptionMethod) extends StreamEncryptionModule
  with BCAsymmetricCipherKeys with BCECKeys {

  private[this] val cipher = ECIESCipherModule.createIesEngine(method)
  private[this] val ephKeyGenerator = ECIESCipherModule.createEphKeyGenerator(keyPairGenerator)

  override def createParameters(): EncryptionParameters = {
    val basicParameters = EncryptionParameters.asymmetric(super.createParameters())
    ECIESCipherModule.addIesParameters(basicParameters)
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val iesParameters = ECIESCipherModule.getIesParameters(parameters.method)
    val publicKey = getCipherKey(parameters, encrypt)
    if (encrypt) {
      cipher.init(publicKey, iesParameters, ephKeyGenerator)
    } else {
      cipher.init(publicKey, iesParameters, new ECIESPublicKeyParser(ECUtils.getCurveDomainParameters(method)))
    }
  }

  def process(data: ByteString): ByteString = {
    val outArray = cipher.processBlock(data.toArray, 0, data.length)
    ByteString(outArray)
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
