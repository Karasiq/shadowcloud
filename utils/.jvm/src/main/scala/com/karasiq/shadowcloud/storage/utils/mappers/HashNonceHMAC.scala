package com.karasiq.shadowcloud.storage.utils.mappers

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{AsymmetricEncryptionParameters, SymmetricEncryptionParameters}
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper

// Chunk key obfuscation utility
private[shadowcloud] class HashNonceHMAC(config: Config) extends ChunkKeyMapper {
  protected object settings extends ConfigImplicits {
    val mapperConfig = config.getConfigIfExists("hash-nonce-hmac-mapper")
    val algorithm = mapperConfig.withDefault("HmacSHA256", _.getString("algorithm"))
  }

  def apply(chunk: Chunk): ByteString = {
    require(chunk.checksum.hash.nonEmpty)
    val nonce = chunk.encryption match {
      case sp: SymmetricEncryptionParameters ⇒ sp.nonce
      case ap: AsymmetricEncryptionParameters ⇒ ap.publicKey
    }
    val hmac = Mac.getInstance(settings.algorithm)
    hmac.init(new SecretKeySpec(nonce.toArray, settings.algorithm))
    val result = hmac.doFinal(chunk.checksum.hash.toArray)
    ByteString(result)
  }
}
