package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import akka.util.ByteString
import org.bouncycastle.crypto.digests.Blake2bDigest

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule, HashingModuleStreamer, OnlyStreamHashingModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.Blake2bModule.DigestOptions

private[bouncycastle] object Blake2bModule {
  def apply(method: HashingMethod = HashingMethod("Blake2b")): HashingModule = {
    new Blake2bModule(DigestOptions(method))
  }

  private case class DigestOptions(method: HashingMethod) {
    import com.karasiq.shadowcloud.config.utils.ConfigImplicits._
    private[this] val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(256, _.getInt("digest-size"))
    val digestKey = config.withDefault(ByteString.empty, _.getHexString("digest-key"))
    val digestSalt = config.withDefault(ByteString.empty, _.getHexString("digest-salt"))
    val digestPersonalization = config.withDefault(ByteString.empty, _.getHexString("digest-personalization"))
  }
}

private[bouncycastle] class Blake2bModule(options: DigestOptions) extends OnlyStreamHashingModule {
  val method: HashingMethod = options.method

  def createStreamer(): HashingModuleStreamer = {
    new Blake2bStreamer
  }

  protected class Blake2bStreamer extends HashingModuleStreamer {
    private[this] val digest = new Blake2bDigest(
      if (options.digestKey.nonEmpty) options.digestKey.toArray else null,
      options.digestSize / 8,
      if (options.digestSalt.nonEmpty) options.digestSalt.toArray else null,
      if (options.digestPersonalization.nonEmpty) options.digestPersonalization.toArray else null,
    )

    def module: HashingModule = {
      Blake2bModule.this
    }

    def update(data: ByteString): Unit = {
      digest.update(data.toArray, 0, data.length)
    }

    def finish(): ByteString = {
      val outArray = new Array[Byte](digest.getDigestSize)
      digest.doFinal(outArray, 0)
      ByteString(outArray)
    }

    def reset(): Unit = {
      digest.reset()
    }
  }
}
