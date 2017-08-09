package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.modes.AEADBlockCipher
import org.bouncycastle.crypto.params.{AEADParameters, KeyParameter}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCSymmetricKeys
import com.karasiq.shadowcloud.crypto.bouncycastle.symmetric.AEADBlockCipherModule.AEADCipherOptions

//noinspection RedundantDefaultArgument
private[bouncycastle] object AEADBlockCipherModule {
  def apply(method: EncryptionMethod): AEADBlockCipherModule = {
    new AEADBlockCipherModule(AEADCipherOptions(method))
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

  private case class AEADCipherOptions(method: EncryptionMethod) {
    import ConfigImplicits._
    private[this] val config = ConfigProps.toConfig(method.config)
    val customBlockSize = config.optional(_.getInt("block-size"))
    val nonceSize = config.withDefault(BCBlockCiphers.getNonceSize(method.algorithm, customBlockSize), _.getInt("nonce-size"))
    val additionalDataSize = config.withDefault(0, _.getInt("ad-size"))
    val macSize = config.withDefault(BCBlockCiphers.getAeadMacSize(method.algorithm, customBlockSize), _.getInt("mac-size"))
  }

  private def toAEADParameters(parameters: EncryptionParameters): AEADParameters = {
    val options = AEADCipherOptions(parameters.method)

    @inline
    def splitNonce(value: ByteString): (ByteString, ByteString) = value.splitAt(options.nonceSize)

    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    val (nonce, additionalData) = splitNonce(symmetricParameters.nonce)

    new AEADParameters(
      new KeyParameter(symmetricParameters.key.toArray),
      options.macSize, nonce.toArray,
      if (additionalData.nonEmpty) additionalData.toArray else null
    )
  }

  private def createAEADCipher(method: EncryptionMethod): AEADBlockCipher = {
    val options = AEADCipherOptions(method)
    BCBlockCiphers.createAEADCipher(options.method.algorithm, options.customBlockSize)
  }
}

private[bouncycastle] class AEADBlockCipherModule(defaultOptions: AEADCipherOptions)
  extends OnlyStreamEncryptionModule with BCSymmetricKeys {

  val method: EncryptionMethod = defaultOptions.method
  protected val nonceSize: Int = defaultOptions.nonceSize + defaultOptions.additionalDataSize

  def createStreamer(): EncryptionModuleStreamer = {
    new AEADBlockCipherStreamer()
  }

  protected class AEADBlockCipherStreamer extends EncryptionModuleStreamer {
    private[this] var aeadCipher: AEADBlockCipher = _

    def module: EncryptionModule = {
      AEADBlockCipherModule.this
    }

    def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
      val aeadParameters = AEADBlockCipherModule.toAEADParameters(parameters)
      aeadCipher = AEADBlockCipherModule.createAEADCipher(parameters.method)
      aeadCipher.init(encrypt, aeadParameters)
    }

    def process(data: ByteString): ByteString = {
      requireInitialized()
      val output = new Array[Byte](aeadCipher.getUpdateOutputSize(data.length))
      val length = aeadCipher.processBytes(data.toArray, 0, data.length, output, 0)
      ByteString.fromArray(output, 0, length)
    }

    def finish(): ByteString = {
      requireInitialized()
      val output = new Array[Byte](aeadCipher.getOutputSize(0))
      val length = aeadCipher.doFinal(output, 0)
      ByteString.fromArray(output, 0, length)
    }

    private[this] def requireInitialized(): Unit = {
      require(aeadCipher ne null, "Not initialized")
    }
  }
}
