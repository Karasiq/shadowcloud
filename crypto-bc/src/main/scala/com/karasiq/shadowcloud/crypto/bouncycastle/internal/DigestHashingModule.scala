package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}
import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.Blake2bDigest

import scala.language.postfixOps

private[bouncycastle] object DigestHashingModule extends ConfigImplicits {
  def Blake2b(method: HashingMethod = HashingMethod("Blake2b")): DigestHashingModule = {
    val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(256, _.getInt("digest-size"))
    new DigestHashingModule(method, new Blake2bDigest(digestSize))
  }
}

private[bouncycastle] final class DigestHashingModule(val method: HashingMethod, digest: Digest) extends StreamHashingModule {
  def update(data: ByteString): Unit = {
    digest.update(data.toArray, 0, data.length)
  }

  def createHash(): ByteString = {
    val outArray = new Array[Byte](digest.getDigestSize)
    digest.doFinal(outArray, 0)
    ByteString(outArray)
  }

  def reset(): Unit = {
    digest.reset()
  }
}
