package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import java.security.SecureRandom

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

import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.config.{ConfigProps, CryptoProps}
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric.ECIESCipherModule.IESOptions
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCUtils, ECUtils}
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.BCECKeys
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.BlockCipherModule
import com.karasiq.shadowcloud.model.crypto.{AsymmetricEncryptionParameters, EncryptionMethod, EncryptionParameters, HashingMethod}

private[bouncycastle] object ECIESCipherModule {
  import ConfigImplicits._


  object defaults {
    val digest = HashingMethod("SHA-512") // HashingMethod("SHA3", config = ConfigProps("digest-size" → 512))
    val encodingSize = 32
    val derivationSize = 32
    val macKeySize = 256
  }

  def apply(method: EncryptionMethod = EncryptionMethod("ECIES")): ECIESCipherModule = {
    new ECIESCipherModule(method)
  }

  private def toIesParameters(options: IESOptions): IESParameters = {
    import options._
    blockCipherMethod match {
      case Some(blockCipherMethod) ⇒
        new IESWithCipherParameters(derivation.toArray, encoding.toArray, macKeySize, blockCipherMethod.keySize)

      case None ⇒
        // Streaming mode
        new IESParameters(derivation.toArray, encoding.toArray, macKeySize)
    }
  }

  private def generateIesParameters(secureRandom: SecureRandom, parameters: AsymmetricEncryptionParameters): AsymmetricEncryptionParameters = {
    def generateHexString(size: Int): String = {
      val bytes = new Array[Byte](size)
      secureRandom.nextBytes(bytes)
      HexString.encode(ByteString.fromArrayUnsafe(bytes))
    }

    val newConfig = ConfigProps.toConfig(parameters.method.config)
      .withValue("ies.derivation", ConfigValueFactory.fromAnyRef(generateHexString(defaults.derivationSize)))
      .withValue("ies.encoding", ConfigValueFactory.fromAnyRef(generateHexString(defaults.encodingSize)))

    parameters.copy(method = parameters.method.copy(config = ConfigProps.fromConfig(newConfig)))
  }

  private def createIesEngine(options: IESOptions): IESEngine = {
    def createBlockCipher(method: Option[EncryptionMethod]): Option[BufferedBlockCipher] = {
      method.map(BlockCipherModule.createBlockCipher)
    }

    val agreement = new ECDHBasicAgreement()
    val kdfGenerator = new KDF2BytesGenerator(BCDigests.createDigest(options.digestMethod))
    val mac = new HMac(BCDigests.createDigest(options.hmacMethod))
    createBlockCipher(options.blockCipherMethod) match {
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

  private case class IESOptions(method: EncryptionMethod) {
    private[this] val iesConfig = ConfigProps.toConfig(method.config).getConfigIfExists("ies")

    val derivation = iesConfig.getHexString("derivation")
    val encoding = iesConfig.getHexString("encoding")
    val macKeySize = iesConfig.withDefault(defaults.macKeySize, _.getInt("mac-key-size"))

    val digestMethod = getDigestMethod("digest").getOrElse(defaults.digest)

    // Will fail if hmac digest is not in list: org.bouncycastle.crypto.macs.HMac#blockLengths
    val hmacMethod = getDigestMethod("hmac").getOrElse(digestMethod)

    val blockCipherMethod = getBlockCipherMethod()

    private[this] def getDigestMethod(key: String): Option[HashingMethod] = {
      iesConfig
        .optional(_.getConfig(key))
        .map(CryptoProps.hashing)
        .orElse(iesConfig.optional(_.getString(key)).map(HashingMethod(_)))
        .filter(_.algorithm.nonEmpty)
    }

    private[this] def getBlockCipherMethod(): Option[EncryptionMethod] = {
      iesConfig
        .optional(_.getConfig("block-cipher"))
        .map(CryptoProps.encryption)
        .orElse(iesConfig.optional(_.getString("block-cipher")).map(EncryptionMethod(_)))
        .filter(_.algorithm.nonEmpty)
    }
  }
}

private[bouncycastle] final class ECIESCipherModule(val method: EncryptionMethod)
  extends BCAsymmetricCipherModule with BCECKeys {

  private[this] lazy val secureRandom = BCUtils.createSecureRandom()

  override def createParameters(): EncryptionParameters = {
    val basicParameters = EncryptionParameters.asymmetric(super.createParameters())
    ECIESCipherModule.generateIesParameters(secureRandom, basicParameters)
  }

  def createStreamer(): EncryptionModuleStreamer = {
    new ECIESEngineStreamer
  }

  protected class ECIESEngineStreamer extends EncryptionModuleStreamer {
    private[this] var iesEngine: IESEngine = _
    private[this] val ephKeyGenerator = ECIESCipherModule.createEphKeyGenerator(method, keyPairGenerator)

    def module: EncryptionModule = {
      ECIESCipherModule.this
    }

    def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
      val asymmetricKey = BCAsymmetricCipherKeys.getCipherKey(parameters, encrypt)
      val options = IESOptions(parameters.method)

      val iesParameters = ECIESCipherModule.toIesParameters(options)
      iesEngine = ECIESCipherModule.createIesEngine(options)

      if (encrypt) {
        iesEngine.init(asymmetricKey, iesParameters, ephKeyGenerator)
      } else {
        iesEngine.init(asymmetricKey, iesParameters, ECIESCipherModule.createPublicKeyParser(method))
      }
    }

    def process(data: ByteString): ByteString = {
      require(iesEngine ne null, "Not initialized")
      val outArray = iesEngine.processBlock(data.toArrayUnsafe, 0, data.length)
      ByteString.fromArrayUnsafe(outArray)
    }

    def finish(): ByteString = {
      ByteString.empty
    }
  }
}
