package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import java.security.SecureRandom

import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.ConfigValueFactory
import org.bouncycastle.crypto.{Digest, KeyEncoder}
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.engines.IESEngine
import org.bouncycastle.crypto.generators.{EphemeralKeyPairGenerator, KDF2BytesGenerator}
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.{AsymmetricKeyParameter, ECPublicKeyParameters, IESParameters}
import org.bouncycastle.crypto.parsers.ECIESPublicKeyParser

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{AsymmetricEncryptionParameters, EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.ECUtils
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.BCECKeys
import com.karasiq.shadowcloud.utils.HexString

private[bouncycastle] object ECIESCipherModule extends ConfigImplicits {
  private[this] val secureRandom = new SecureRandom()

  def apply(method: EncryptionMethod = EncryptionMethod("ECIES")): ECIESCipherModule = {
    new ECIESCipherModule(method)
  }

  private def getDigest(method: EncryptionMethod): Digest = {
    val digest = ConfigProps.toConfig(method.config).withDefault("SHA1", _.getString("ies-digest"))
    BCDigests.createDigest(digest)
  }

  private def getIesParameters(method: EncryptionMethod): IESParameters = {
    val config = ConfigProps.toConfig(method.config)
    def bytesOrNull(name: String): Array[Byte] = config.withDefault(null, cfg ⇒ HexString.decode(cfg.getString(name)).toArray)
    new IESParameters(bytesOrNull("ies-derivation"), bytesOrNull("ies-encoding"), config.withDefault(64, _.getInt("ies-mac-key-size")))
  }

  private def createIesParameters(parameters: AsymmetricEncryptionParameters): AsymmetricEncryptionParameters = {
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
}

private[bouncycastle] final class ECIESCipherModule(val method: EncryptionMethod) extends StreamEncryptionModule with BCAsymmetricCipherKeys with BCECKeys {
  // TODO: Block cipher mode
  private[this] val cipher = new IESEngine(
    new ECDHBasicAgreement(),
    new KDF2BytesGenerator(ECIESCipherModule.getDigest(method)),
    new HMac(ECIESCipherModule.getDigest(method))
  )

  //noinspection ConvertExpressionToSAM
  private[this] val ephKPG = new EphemeralKeyPairGenerator(keyPairGenerator, new KeyEncoder {
    def getEncoded(keyParameter: AsymmetricKeyParameter): Array[Byte] = {
      keyParameter.asInstanceOf[ECPublicKeyParameters].getQ.getEncoded(false)
    }
  })

  override def createParameters(): EncryptionParameters = {
    val basicParameters = super.createParameters().asymmetric
    ECIESCipherModule.createIesParameters(basicParameters)
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val iesParameters = ECIESCipherModule.getIesParameters(parameters.method)
    val publicKey = getCipherKey(parameters, encrypt)
    if (encrypt) {
      cipher.init(publicKey, iesParameters, ephKPG)
    } else {
      cipher.init(publicKey, iesParameters, new ECIESPublicKeyParser(ECUtils.getCurveDomainParameters(method)))
    }
  }

  def process(data: ByteString): ByteString = {
    ByteString(cipher.processBlock(data.toArray, 0, data.length))
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
