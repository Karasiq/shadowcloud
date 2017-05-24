package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import java.nio.ByteBuffer

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.{AEADBlockCipher, GCMBlockCipher}
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCSymmetricKeys

private[bouncycastle] object AEADBlockCipherModule extends ConfigImplicits {
  def AES_GCM(method: EncryptionMethod = EncryptionMethod("AES/GCM", 256)): AEADBlockCipherModule = {
    val config = ConfigProps.toConfig(method.config)
    val ivSize = config.withDefault(12, _.getInt("iv-size"))
    new AEADBlockCipherModule(new GCMBlockCipher(new AESEngine), ivSize, method)
  }
}

private[bouncycastle] final class AEADBlockCipherModule(private[this] val cipher: AEADBlockCipher,
                                                        protected val nonceSize: Int,
                                                        protected val method: EncryptionMethod)
  extends StreamEncryptionModule with BCSymmetricKeys {

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
}
