package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.modes.AEADBlockCipher
import org.bouncycastle.crypto.params.{AEADParameters, KeyParameter, ParametersWithIV}

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCSymmetricKeys

//noinspection RedundantDefaultArgument
private[bouncycastle] object AEADBlockCipherModule extends ConfigImplicits {
  def apply(method: EncryptionMethod): AEADBlockCipherModule = {
    val config = ConfigProps.toConfig(method.config)
    val customBlockSize = config.optional(_.getInt("block-size"))
    val nonceSize = config.withDefault(BCBlockCiphers.getNonceSize(method.algorithm, customBlockSize), _.getInt("nonce-size"))
    val additionalDataSize = config.withDefault(0, _.getInt("ad-size"))
    val macSize = config.withDefault(BCBlockCiphers.getAeadMacSize(method.algorithm, customBlockSize), _.getInt("mac-size"))
    new AEADBlockCipherModule(method, BCBlockCiphers.createAEADCipher(method.algorithm, customBlockSize), nonceSize, additionalDataSize, macSize)
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

private[bouncycastle] class AEADBlockCipherModule(val method: EncryptionMethod,
                                                  protected val aeadCipher: AEADBlockCipher,
                                                  pNonceSize: Int,
                                                  additionalDataSize: Int,
                                                  macSize: Int)
  extends StreamEncryptionModule with BCSymmetricKeys {

  override protected val nonceSize: Int = pNonceSize + additionalDataSize

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    @inline def splitNonce(value: ByteString): (ByteString, ByteString) = value.splitAt(pNonceSize)

    val sp = EncryptionParameters.symmetric(parameters)
    val (nonce, additionalData) = splitNonce(sp.nonce)
    // assert(nonce.length == pNonceSize && additionalData.length == additionalDataSize)

    val aeadParameters = new AEADParameters(
      new KeyParameter(sp.key.toArray),
      macSize, nonce.toArray,
      if (additionalData.nonEmpty) additionalData.toArray else null
    ) // new ParametersWithIV(new KeyParameter(sp.key.toArray), nonce.toArray)

    try {
      aeadCipher.init(encrypt, aeadParameters)
    } catch { case error: IllegalArgumentException ⇒
      // Fix com/karasiq/shadowcloud/test/crypto/EncryptionModuleTest.scala:53, com/karasiq/shadowcloud/crypto/bouncycastle/test/BouncyCastleTest.scala:139
      System.err.println(error)
      aeadCipher.init(encrypt, new ParametersWithIV(null, Array.ofDim[Byte](pNonceSize)))
      aeadCipher.init(encrypt, aeadParameters)
    }
  }

  def process(data: ByteString): ByteString = {
    val output = new Array[Byte](aeadCipher.getUpdateOutputSize(data.length))
    val length = aeadCipher.processBytes(data.toArray, 0, data.length, output, 0)
    ByteString.fromArray(output, 0, length)
  }

  def finish(): ByteString = {
    val output = new Array[Byte](aeadCipher.getOutputSize(0))
    val length = aeadCipher.doFinal(output, 0)
    ByteString.fromArray(output, 0, length)
  }
}
