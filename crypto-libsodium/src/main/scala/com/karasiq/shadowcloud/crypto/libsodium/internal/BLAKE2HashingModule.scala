package com.karasiq.shadowcloud.crypto.libsodium.internal

import java.util

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}
import org.abstractj.kalium.NaCl
import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.Util

private[libsodium] final class BLAKE2HashingModule(val method: HashingMethod) extends StreamHashingModule {
  private[this] val sodium = NaCl.sodium()
  private[this] val stateSize = sodium.crypto_generichash_statebytes()
  private[this] val state = new Array[Byte](stateSize)
  this.reset()

  def update(data: ByteString): Unit = {
    val array = data.toArray
    sodium.crypto_generichash_update(state, array, array.length)
  }

  def createHash(): ByteString = {
    val outSize = Sodium.CRYPTO_GENERICHASH_BYTES
    val out = Util.zeros(outSize)
    sodium.crypto_generichash_final(state, out, outSize)
    ByteString(out)
  }

  def reset(): Unit = {
    util.Arrays.fill(state, 0.toByte)
    sodium.crypto_generichash_init(state, Array.emptyByteArray, 0, stateSize)
  }
}
