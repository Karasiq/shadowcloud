package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.KeyGenerator

import akka.util.ByteString
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.{AEADBlockCipher, GCMBlockCipher}
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import scala.language.postfixOps

private[bouncycastle] object AEADBlockCipherEncryptionModule extends ConfigImplicits {
  def AES_GCM(method: EncryptionMethod = EncryptionMethod("AES/GCM", 256)): AEADBlockCipherEncryptionModule = {
    val config = ConfigProps.toConfig(method.config)
    val ivSize = config.withDefault(12, _.getInt("iv-size"))
    new AEADBlockCipherEncryptionModule(new GCMBlockCipher(new AESEngine), "AES", ivSize, method)
  }
}

private[bouncycastle] final class AEADBlockCipherEncryptionModule(cipher: AEADBlockCipher, keyAlg: String, ivSize: Int,
                                                                  method: EncryptionMethod) extends StreamEncryptionModule {
  private[this] val secureRandom = new SecureRandom()
  private[this] val keyGenerator = KeyGenerator.getInstance(keyAlg, BCUtils.provider)
  keyGenerator.init(method.keySize, secureRandom)

  def createParameters(): EncryptionParameters = {
    SymmetricEncryptionParameters(method, ByteString(keyGenerator.generateKey().getEncoded), generateIV())
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    parameters.symmetric.copy(nonce = generateIV())
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val symParameters = parameters.symmetric
    val key = symParameters.key.toArray
    val iv = symParameters.nonce.toArray
    val keyParams = new ParametersWithIV(new KeyParameter(key), iv)
    try {
      cipher.init(encrypt, keyParams)
    } catch { case _: IllegalArgumentException ⇒
      cipher.init(encrypt, new ParametersWithIV(new KeyParameter(key), Array[Byte](0)))
      cipher.init(encrypt, keyParams)
    }
  }

  def process(data: ByteString): ByteString = {
    val output = new Array[Byte](cipher.getUpdateOutputSize(data.length))
    val length = cipher.processBytes(data.toArray, 0, data.length, output, 0)
    ByteString(ByteBuffer.wrap(output, 0, length))
  }

  def finish(): ByteString = {
    val output = new Array[Byte](cipher.getOutputSize(0))
    val length = cipher.doFinal(output, 0)
    ByteString(ByteBuffer.wrap(output, 0, length))
  }

  private[this] def generateIV(): ByteString = {
    val iv = new Array[Byte](ivSize)
    secureRandom.nextBytes(iv)
    ByteString(iv)
  }
}
