package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.BlockCipher
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCSymmetricKeys

//noinspection RedundantDefaultArgument
private[bouncycastle] object BlockCipherModule extends ConfigImplicits {
  def apply(method: EncryptionMethod): BlockCipherModule = {
    val config = ConfigProps.toConfig(method.config)
    val customBlockSize = config.optional(_.getInt("block-size"))
    val nonceSize = config.withDefault(BCBlockCiphers.getNonceSize(method.algorithm, customBlockSize), _.getInt("nonce-size"))
    new BlockCipherModule(method, BCBlockCiphers.createBlockCipher(method.algorithm, customBlockSize), nonceSize)
  }

  def AES_CBC(): BlockCipherModule = {
    apply(EncryptionMethod("AES/CBC", 256))
  }

  def AES_CFB(): BlockCipherModule = {
    apply(EncryptionMethod("AES/CFB", 256))
  }

  def AES_OFB(): BlockCipherModule = {
    apply(EncryptionMethod("AES/OFB", 256))
  }
}

private[bouncycastle] class BlockCipherModule(val method: EncryptionMethod,
                                              protected val baseCipher: BlockCipher,
                                              protected val nonceSize: Int)
  extends StreamEncryptionModule with BCSymmetricKeys {

  protected val bufferedCipher = BCBlockCiphers.toPaddedBufferedBlockCipher(baseCipher)

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val sp = EncryptionParameters.symmetric(parameters)
    val keyParams = new ParametersWithIV(new KeyParameter(sp.key.toArray), sp.nonce.toArray)
    try {
      bufferedCipher.init(encrypt, keyParams)
    } catch { case _: IllegalArgumentException ⇒
      bufferedCipher.init(encrypt, new ParametersWithIV(keyParams.getParameters, Array[Byte](0)))
      bufferedCipher.init(encrypt, keyParams)
    }
  }

  def process(data: ByteString): ByteString = {
    val outArray = new Array[Byte](bufferedCipher.getUpdateOutputSize(data.length))
    val length = bufferedCipher.processBytes(data.toArray, 0, data.length, outArray, 0)
    ByteString.fromArray(outArray, 0, length)
  }

  def finish(): ByteString = {
    val output = new Array[Byte](bufferedCipher.getOutputSize(0))
    val length = bufferedCipher.doFinal(output, 0)
    ByteString.fromArray(output, 0, length)
  }
}
