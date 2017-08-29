package com.karasiq.shadowcloud.storage.utils.mappers

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.model.{Chunk, ChunkId}
import com.karasiq.shadowcloud.model.crypto.{AsymmetricEncryptionParameters, SymmetricEncryptionParameters}
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper

// Chunk key obfuscation utility
private[shadowcloud] class HashNonceHMACKeyMapper(config: Config) extends ChunkKeyMapper {
  protected object settings extends ConfigImplicits {
    val iterations = math.max(1, config.withDefault(1, _.getInt("iterations")))
    val algorithm = config.withDefault("HmacSHA256", _.getString("algorithm"))
  }

  def apply(chunk: Chunk): ChunkId = {
    require(chunk.checksum.hash.nonEmpty)
    val nonce = chunk.encryption match {
      case sp: SymmetricEncryptionParameters ⇒ sp.nonce
      case ap: AsymmetricEncryptionParameters ⇒ ap.publicKey
    }
    val hmac = Mac.getInstance(settings.algorithm)
    hmac.init(new SecretKeySpec(nonce.toArray, settings.algorithm))

    var result = chunk.checksum.hash.toArray
    for (_ ← 1 to settings.iterations) {
      hmac.reset()
      result = hmac.doFinal(result)
    }

    ByteString(result)
  }
}
