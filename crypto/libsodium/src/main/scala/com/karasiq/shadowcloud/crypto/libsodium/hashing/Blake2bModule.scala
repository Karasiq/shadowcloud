package com.karasiq.shadowcloud.crypto.libsodium.hashing

import java.util

import akka.util.ByteString
import org.abstractj.kalium.NaCl
import org.abstractj.kalium.crypto.Util

import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.{HashingModule, HashingModuleStreamer, StreamHashingModule}
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

    require(digestKey.isEmpty || (digestKey.length >= NaCl.Sodium.CRYPTO_GENERICHASH_KEYBYTES_MIN &&
      digestKey.length <= NaCl.Sodium.CRYPTO_GENERICHASH_KEYBYTES_MAX), "Invalid digest key")
  }
}

private[libsodium] final class Blake2bModule(options: DigestOptions) extends StreamHashingModule {
  val method: HashingMethod = options.method
  private[this] val outBytes = options.digestSize / 8
  private[this] val sodium = NaCl.sodium()

  def createStreamer(): HashingModuleStreamer = {
    new Blake2bStreamer
  }

  def createHash(data: ByteString): ByteString = {
    val outArray = new Array[Byte](outBytes)
    sodium.crypto_generichash(
      outArray, outArray.length,
      ByteStringUnsafe.getArray(data), data.length,
      ByteStringUnsafe.getArray(options.digestKey), options.digestKey.length
    )
    ByteString.fromArrayUnsafe(outArray)
  }

  protected class Blake2bStreamer extends HashingModuleStreamer {
    private[this] val state = new Array[Byte](sodium.crypto_generichash_statebytes())

    this.reset()

    def module: HashingModule = {
      Blake2bModule.this
    }

    def reset(): Unit = {
      util.Arrays.fill(state, 0.toByte)
      sodium.crypto_generichash_init(state, options.digestKey.toArray, options.digestKey.length, outBytes)
    }

    def update(data: ByteString): Unit = {
      val array = data.toArrayUnsafe
      sodium.crypto_generichash_update(state, array, array.length)
    }

    def finish(): ByteString = {
      val out = Util.zeros(outBytes)
      sodium.crypto_generichash_final(state, out, outBytes)
      ByteString.fromArrayUnsafe(out)
    }
  }
}
