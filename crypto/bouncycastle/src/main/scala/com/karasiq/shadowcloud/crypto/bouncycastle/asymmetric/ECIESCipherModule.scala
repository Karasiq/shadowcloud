package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.ConfigValueFactory
import org.bouncycastle.crypto._
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.engines.IESEngine
import org.bouncycastle.crypto.generators.{EphemeralKeyPairGenerator, KDF2BytesGenerator}
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.{AsymmetricKeyParameter, ECPublicKeyParameters, IESParameters, IESWithCipherParameters}
import org.bouncycastle.crypto.parsers.ECIESPublicKeyParser

import com.karasiq.shadowcloud.config.{ConfigProps, CryptoProps}
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCUtils, ECUtils}
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.BCECKeys
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.BCBlockCiphers
import com.karasiq.shadowcloud.utils.HexString

private[bouncycastle] object ECIESCipherModule extends ConfigImplicits {
  object defaults {
    val digest = HashingMethod("SHA-512") // HashingMethod("SHA3", config = ConfigProps("digest-size" → 512))
    val encodingSize = 32
    val derivationSize = 32
    val macKeySize = 256
  }

  def apply(method: EncryptionMethod = EncryptionMethod("ECIES")): ECIESCipherModule = {
    new ECIESCipherModule(method)
  }

  private def getDigestMethod(config: Config, key: String): Option[HashingMethod] = {
    config
      .optional(_.getConfig(key))
      .map(CryptoProps.hashing)
      .orElse(config.optional(_.getString(key)).map(HashingMethod(_)))
      .filter(_.algorithm.nonEmpty)
  }

  private def getBlockCipherMethod(config: Config): Option[EncryptionMethod] = {
    config
      .optional(_.getConfig("ies.block-cipher"))
      .map(CryptoProps.encryption)
      .orElse(config.optional(_.getString("ies.block-cipher")).map(EncryptionMethod(_)))
      .filter(_.algorithm.nonEmpty)
  }

  private def getIesParameters(method: EncryptionMethod): IESParameters = {
    val config = ConfigProps.toConfig(method.config)

    def getByteArray(name: String): Array[Byte] = {
      config.optional(_.getString(name))
        .map(HexString.decode)
        .map(_.toArray)
        .getOrElse(throw new IllegalArgumentException(name + " required"))
      
      // config.withDefault(null, cfg ⇒ HexString.decode(cfg.getString(name)).toArray)
    }

    val derivation = getByteArray("ies.derivation")
    val encoding = getByteArray("ies.encoding")
    val macKeySize = config.withDefault(defaults.macKeySize, _.getInt("ies.mac-key-size"))

    getBlockCipherMethod(config) match {
      case Some(bcMethod) ⇒
        new IESWithCipherParameters(derivation, encoding, macKeySize, bcMethod.keySize)

      case None ⇒
        // Streaming mode
        new IESParameters(derivation, encoding, macKeySize)
    }
  }

  private def addIesParameters(parameters: AsymmetricEncryptionParameters): AsymmetricEncryptionParameters = {
    val secureRandom = BCUtils.createSecureRandom()

    def randomBytes(size: Int): String = {
      val bytes = new Array[Byte](size)
      secureRandom.nextBytes(bytes)
      HexString.encode(ByteString(bytes))
    }

    val newConfig = ConfigProps.toConfig(parameters.method.config)
      .withValue("ies.derivation", ConfigValueFactory.fromAnyRef(randomBytes(defaults.derivationSize)))
      .withValue("ies.encoding", ConfigValueFactory.fromAnyRef(randomBytes(defaults.encodingSize)))
    parameters.copy(method = parameters.method.copy(config = ConfigProps.fromConfig(newConfig)))
  }

  private def createIesEngine(method: EncryptionMethod): IESEngine = {
    def createDigest(config: Config): Digest = {
      val digestMethod = getDigestMethod(config, "ies.digest").getOrElse(defaults.digest)
      BCDigests.createDigest(digestMethod)
    }

    def createHMac(config: Config): HMac = {
      // Will fail if digest is not in list: org.bouncycastle.crypto.macs.HMac#blockLengths
      val digestMethod = getDigestMethod(config, "ies.hmac")
        .orElse(getDigestMethod(config, "ies.digest"))
        .getOrElse(defaults.digest)

      new HMac(BCDigests.createDigest(digestMethod))
    }

    def createBlockCipher(config: Config): Option[BufferedBlockCipher] = {
      val bcMethod = getBlockCipherMethod(config)
      val blockSize = bcMethod
        .map(m ⇒ ConfigProps.toConfig(m.config))
        .flatMap(_.optional(_.getInt("block-size")))
      bcMethod.map(m ⇒ BCBlockCiphers.toPaddedBufferedBlockCipher(BCBlockCiphers.createBlockCipher(m.algorithm, blockSize)))
    }

    val config = ConfigProps.toConfig(method.config)
    val agreement = new ECDHBasicAgreement()
    val kdfGenerator = new KDF2BytesGenerator(createDigest(config))
    val mac = createHMac(config)
    createBlockCipher(config) match {
      case Some(blockCipher) ⇒
        new IESEngine(agreement, kdfGenerator, mac, blockCipher)

      case None ⇒
        // Streaming mode
        new IESEngine(agreement, kdfGenerator, mac)
    }
  }

  //noinspection ConvertExpressionToSAM
  private def createEphKeyGenerator(method: EncryptionMethod, keyPairGenerator: AsymmetricCipherKeyPairGenerator): EphemeralKeyPairGenerator = {
    val compressPoints = ConfigProps.toConfig(method.config).withDefault(true, _.getBoolean("compress-ec-points"))
    new EphemeralKeyPairGenerator(keyPairGenerator, new KeyEncoder {
      def getEncoded(keyParameter: AsymmetricKeyParameter): Array[Byte] = {
        keyParameter.asInstanceOf[ECPublicKeyParameters].getQ.getEncoded(compressPoints)
      }
    })
  }

  private def createPublicKeyParser(method: EncryptionMethod): KeyParser = {
    val domainParameters = ECUtils.getCurveDomainParameters(method)
    new ECIESPublicKeyParser(domainParameters)
  }
}

private[bouncycastle] final class ECIESCipherModule(val method: EncryptionMethod) extends StreamEncryptionModule
  with BCAsymmetricCipherKeys with BCECKeys {

  private[this] var iesEngine: IESEngine = _
  private[this] val ephKeyGenerator = ECIESCipherModule.createEphKeyGenerator(method, keyPairGenerator)

  override def createParameters(): EncryptionParameters = {
    val basicParameters = EncryptionParameters.asymmetric(super.createParameters())
    ECIESCipherModule.addIesParameters(basicParameters)
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val asymmetricKey = getCipherKey(parameters, encrypt)
    val iesParameters = ECIESCipherModule.getIesParameters(parameters.method)

    iesEngine = ECIESCipherModule.createIesEngine(parameters.method)
    if (encrypt) {
      iesEngine.init(asymmetricKey, iesParameters, ephKeyGenerator)
    } else {
      iesEngine.init(asymmetricKey, iesParameters, ECIESCipherModule.createPublicKeyParser(method))
    }
  }

  def process(data: ByteString): ByteString = {
    require(iesEngine ne null, "Not initialized")
    val outArray = iesEngine.processBlock(data.toArray, 0, data.length)
    ByteString(outArray)
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
