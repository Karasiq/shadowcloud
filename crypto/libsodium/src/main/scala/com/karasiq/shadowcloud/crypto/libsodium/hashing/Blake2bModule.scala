package com.karasiq.shadowcloud.crypto.libsodium.hashing

import java.util

import akka.util.ByteString
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}
import org.abstractj.kalium.NaCl
import org.abstractj.kalium.crypto.Util

private[libsodium] object Blake2bModule extends ConfigImplicits {
  def apply(method: HashingMethod = HashingMethod("Blake2b")): Blake2bModule = {
    val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(256, _.getInt("digest-size"))
    new Blake2bModule(method, digestSize)
  }
}

private[libsodium] final class Blake2bModule(val method: HashingMethod, size: Int) extends StreamHashingModule {
  private[this] val sodium = NaCl.sodium()
  private[this] val stateSize = sodium.crypto_generichash_statebytes()
  private[this] val outBytes = size / 8
  private[this] val state = new Array[Byte](stateSize)
  this.reset()

  def update(data: ByteString): Unit = {
    val array = data.toArray
    sodium.crypto_generichash_update(state, array, array.length)
  }

  def createHash(): ByteString = {
    val out = Util.zeros(outBytes)
    sodium.crypto_generichash_final(state, out, outBytes)
    ByteString(out)
  }

  def reset(): Unit = {
    util.Arrays.fill(state, 0.toByte)
    sodium.crypto_generichash_init(state, Array.emptyByteArray, 0, outBytes)
  }
}
