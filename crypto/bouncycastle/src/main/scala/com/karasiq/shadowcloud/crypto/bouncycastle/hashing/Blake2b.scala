package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import akka.util.ByteString
import org.bouncycastle.crypto.digests.Blake2bDigest

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.model.crypto.HashingMethod

private[bouncycastle] object Blake2b {
  private case class Blake2bOptions(method: HashingMethod) {
    import com.karasiq.shadowcloud.config.utils.ConfigImplicits._
    private[this] val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(256, _.getInt("digest-size"))
    val digestKey = config.withDefault(ByteString.empty, _.getHexString("digest-key"))
    val digestSalt = config.withDefault(ByteString.empty, _.getHexString("digest-salt"))
    val digestPersonalization = config.withDefault(ByteString.empty, _.getHexString("digest-personalization"))
  }

  private[bouncycastle] def createDigest(method: HashingMethod): Blake2bDigest = {
    val options = Blake2bOptions(method)
    new Blake2bDigest(
      if (options.digestKey.nonEmpty) options.digestKey.toArray else null,
      options.digestSize / 8,
      if (options.digestSalt.nonEmpty) options.digestSalt.toArray else null,
      if (options.digestPersonalization.nonEmpty) options.digestPersonalization.toArray else null,
    )
  }
}
