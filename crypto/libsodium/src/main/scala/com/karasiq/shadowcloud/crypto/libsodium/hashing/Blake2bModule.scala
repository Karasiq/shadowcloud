package com.karasiq.shadowcloud.crypto.libsodium.hashing

import akka.util.ByteString
import org.abstractj.kalium.NaCl

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.HashingModule
import com.karasiq.shadowcloud.crypto.libsodium.hashing.Blake2bModule.DigestOptions
import com.karasiq.shadowcloud.model.crypto.HashingMethod
import com.karasiq.shadowcloud.utils.ByteStringUnsafe

private[libsodium] object Blake2bModule {
  def apply(method: HashingMethod = HashingMethod("Blake2b")): Blake2bModule = {
    new Blake2bModule(DigestOptions(method))
  }

  private case class DigestOptions(method: HashingMethod) {
    import ConfigImplicits._
    private[this] val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(256, _.getInt("digest-size"))
    val digestKey = config.withDefault(ByteString.empty, _.getHexString("digest-key"))

    require(digestKey.isEmpty || (digestKey.length >= NaCl.Sodium.CRYPTO_GENERICHASH_BLAKE2B_BYTES_MIN &&
      digestKey.length <= NaCl.Sodium.CRYPTO_GENERICHASH_BLAKE2B_BYTES_MAX), "Invalid digest key")
  }
}

private[libsodium] final class Blake2bModule(options: DigestOptions) extends HashingModule {
  val method: HashingMethod = options.method
  private[this] val outBytes = options.digestSize / 8
  private[this] val sodium = NaCl.sodium()

  def createHash(data: ByteString): ByteString = {
    val outArray = new Array[Byte](outBytes)
    sodium.crypto_generichash_blake2b(
      outArray, outArray.length,
      ByteStringUnsafe.getArray(data), data.length,
      ByteStringUnsafe.getArray(options.digestKey), options.digestKey.length
    )
    ByteString.fromArrayUnsafe(outArray)
  }
}
