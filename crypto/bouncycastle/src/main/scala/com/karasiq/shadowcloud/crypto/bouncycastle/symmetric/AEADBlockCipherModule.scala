package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.modes.AEADBlockCipher
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCSymmetricKeys

//noinspection RedundantDefaultArgument
private[bouncycastle] object AEADBlockCipherModule extends ConfigImplicits {
  def apply(method: EncryptionMethod): AEADBlockCipherModule = {
    val config = ConfigProps.toConfig(method.config)
    val ivSize = config.withDefault(12, _.getInt("iv-size"))
    new AEADBlockCipherModule(method, BCBlockCiphers.createAEADCipher(method.algorithm), ivSize)
  }

  def AES_GCM(): AEADBlockCipherModule = {
    apply(EncryptionMethod("AES/GCM", 256))
  }

  def AES_CCM(): AEADBlockCipherModule = {
    apply(EncryptionMethod("AES/CCM", 256))
  }

  def AES_EAX(): AEADBlockCipherModule = {
    apply(EncryptionMethod("AES/EAX", 256))
  }

  def AES_OCB(): AEADBlockCipherModule = {
    apply(EncryptionMethod("AES/OCB", 256))
  }
}

private[bouncycastle] final class AEADBlockCipherModule(val method: EncryptionMethod,
                                                        private[this] val cipher: AEADBlockCipher,
                                                        protected val nonceSize: Int)
  extends StreamEncryptionModule with BCSymmetricKeys {

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val sp = EncryptionParameters.symmetric(parameters)
    val keyParams = new ParametersWithIV(new KeyParameter(sp.key.toArray), sp.nonce.toArray)
    try {
      cipher.init(encrypt, keyParams)
    } catch { case _: IllegalArgumentException ⇒
      cipher.init(encrypt, new ParametersWithIV(keyParams.getParameters, Array[Byte](0)))
      cipher.init(encrypt, keyParams)
    }
  }

  def process(data: ByteString): ByteString = {
    val output = new Array[Byte](cipher.getUpdateOutputSize(data.length))
    val length = cipher.processBytes(data.toArray, 0, data.length, output, 0)
    ByteString.fromArray(output, 0, length)
  }

  def finish(): ByteString = {
    val output = new Array[Byte](cipher.getOutputSize(0))
    val length = cipher.doFinal(output, 0)
    ByteString.fromArray(output, 0, length)
  }
}
