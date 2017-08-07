package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCSymmetricKeys, BCUtils}
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.BlockCipherModule.BlockCipherOptions

//noinspection RedundantDefaultArgument
private[bouncycastle] object BlockCipherModule {
  def apply(method: EncryptionMethod): BlockCipherModule = {
    new BlockCipherModule(BlockCipherOptions(method))
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

  def AES_CTR(): BlockCipherModule = {
    apply(EncryptionMethod("AES/CTR", 256))
  }

  private case class BlockCipherOptions(method: EncryptionMethod) {
    import ConfigImplicits._
    private[this] val config = ConfigProps.toConfig(method.config)
    val customBlockSize = config.optional(_.getInt("block-size"))
    val nonceSize = config.withDefault(BCBlockCiphers.getNonceSize(method.algorithm, customBlockSize), _.getInt("nonce-size"))
  }
}

private[bouncycastle] class BlockCipherModule(defaultOptions: BlockCipherOptions)
  extends StreamEncryptionModule with BCSymmetricKeys {

  val method: EncryptionMethod = defaultOptions.method
  protected val nonceSize: Int = defaultOptions.nonceSize
  protected var cipher: PaddedBufferedBlockCipher = _

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val options = BlockCipherOptions(parameters.method)
    val baseCipher = BCBlockCiphers.createBlockCipher(method.algorithm, options.customBlockSize)
    cipher = BCBlockCiphers.toPaddedBufferedBlockCipher(baseCipher)
    cipher.init(encrypt, BCUtils.toParametersWithIV(parameters))
  }

  def process(data: ByteString): ByteString = {
    requireInitialized()
    val outArray = new Array[Byte](cipher.getUpdateOutputSize(data.length))
    val outLength = cipher.processBytes(data.toArray, 0, data.length, outArray, 0)
    ByteString.fromArray(outArray, 0, outLength)
  }

  def finish(): ByteString = {
    requireInitialized()
    val outArray = new Array[Byte](cipher.getOutputSize(0))
    val outLength = cipher.doFinal(outArray, 0)
    ByteString.fromArray(outArray, 0, outLength)
  }

  private[this] def requireInitialized(): Unit = {
    require(cipher ne null, "Not initialized")
  }
}
