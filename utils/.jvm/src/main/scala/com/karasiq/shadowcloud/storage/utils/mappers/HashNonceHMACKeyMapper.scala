package com.karasiq.shadowcloud.storage.utils.mappers

import akka.util.ByteString
import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.crypto.{AsymmetricEncryptionParameters, SymmetricEncryptionParameters}
import com.karasiq.shadowcloud.model.{Chunk, ChunkId}
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper
import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
import com.typesafe.config.Config
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Chunk key obfuscation utility
private[shadowcloud] class HashNonceHMACKeyMapper(config: Config) extends ChunkKeyMapper {
  protected object settings extends ConfigImplicits {
    val iterations    = math.max(1, config.withDefault(1, _.getInt("iterations")))
    val algorithm     = config.withDefault("HmacSHA256", _.getString("algorithm"))
    val usePrivateKey = config.withDefault(false, _.getBoolean("use-private-key"))
  }

  def apply(chunk: Chunk): ChunkId = {
    require(chunk.checksum.hash.nonEmpty)
    val nonce = chunk.encryption match {
      case sp: SymmetricEncryptionParameters  ⇒ if (settings.usePrivateKey) sp.key else sp.nonce
      case ap: AsymmetricEncryptionParameters ⇒ if (settings.usePrivateKey) ap.privateKey else ap.publicKey
    }
    val hmac = Mac.getInstance(settings.algorithm)
    hmac.init(new SecretKeySpec(nonce.toArrayUnsafe, settings.algorithm))

    var result = chunk.checksum.hash.toArrayUnsafe
    for (_ ← 1 to settings.iterations) {
      hmac.reset()
      result = hmac.doFinal(result)
    }

    ByteString.fromArrayUnsafe(result)
  }
}
