package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import java.security.SecureRandom

import scala.language.postfixOps
import scala.util.control.NonFatal

import akka.util.ByteString
import com.typesafe.config.ConfigValueFactory
import org.bouncycastle.crypto.KeyEncoder
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.engines.IESEngine
import org.bouncycastle.crypto.generators.{EphemeralKeyPairGenerator, KDF2BytesGenerator}
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.{AsymmetricKeyParameter, ECPublicKeyParameters, IESParameters}
import org.bouncycastle.crypto.parsers.ECIESPublicKeyParser
import org.bouncycastle.crypto.util.DigestFactory

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.ECUtils
import com.karasiq.shadowcloud.crypto.bouncycastle.sign.BCECKeys
import com.karasiq.shadowcloud.utils.HexString

private[bouncycastle] object ECIESCipherModule {
  def apply(method: EncryptionMethod = EncryptionMethod("ECIES")): ECIESCipherModule = {
    new ECIESCipherModule(method)
  }
}

private[bouncycastle] final class ECIESCipherModule(val method: EncryptionMethod) extends StreamEncryptionModule with BCAsymmetricCipherKeys with BCECKeys {
  // TODO: Block cipher mode
  private[this] val cipher = new IESEngine(
    new ECDHBasicAgreement(),
    new KDF2BytesGenerator(DigestFactory.createSHA1()),
    new HMac(DigestFactory.createSHA1())
  )

  private[this] val secureRandom = new SecureRandom()

  //noinspection ConvertExpressionToSAM
  private[this] val ephKeyGen = new EphemeralKeyPairGenerator(keyPairGenerator, new KeyEncoder {
    def getEncoded(keyParameter: AsymmetricKeyParameter): Array[Byte] = {
      keyParameter.asInstanceOf[ECPublicKeyParameters].getQ.getEncoded(false)
    }
  })

  override def createParameters(): EncryptionParameters = {
    def generateHexString(size: Int): String = {
      val bytes = new Array[Byte](size)
      secureRandom.nextBytes(bytes)
      HexString.encode(ByteString(bytes))
    }
    val parameters = super.createParameters().asymmetric
    val newConfig = ConfigProps.toConfig(parameters.method.config)
      .withValue("ies-derivation", ConfigValueFactory.fromAnyRef(generateHexString(8)))
      .withValue("ies-encoding", ConfigValueFactory.fromAnyRef(generateHexString(8)))
    parameters.copy(method = parameters.method.copy(config = ConfigProps.fromConfig(newConfig)))
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    import com.karasiq.shadowcloud.config.utils.ConfigImplicits._
    require(cipher.ne(null), "No IES engine")
    val config = ConfigProps.toConfig(parameters.method.config)
    def readHexString(name: String): Array[Byte] = {
      try {
        HexString.decode(config.getString(name)).toArray
      } catch { case NonFatal(_) ⇒
        null
      }
    }
    val params = new IESParameters(readHexString("ies-derivation"), readHexString("ies-encoding"), config.withDefault(64, _.getInt("ies-mac-key-size")))
    val publicKey = getCipherKey(parameters, encrypt)
    if (encrypt) {
      cipher.init(publicKey, params, ephKeyGen)
    } else {
      cipher.init(publicKey, params, new ECIESPublicKeyParser(ECUtils.getCurveDomainParameters(method)))
    }
  }

  def process(data: ByteString): ByteString = {
    ByteString(cipher.processBlock(data.toArray, 0, data.length))
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
