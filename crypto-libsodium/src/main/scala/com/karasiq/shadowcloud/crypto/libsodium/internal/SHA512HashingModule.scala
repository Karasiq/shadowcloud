package com.karasiq.shadowcloud.crypto.libsodium.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule}
import org.abstractj.kalium.crypto.Hash

private[libsodium] final class SHA512HashingModule(val method: HashingMethod) extends HashingModule {
  private[this] val hash = new Hash()

  def createHash(data: ByteString): ByteString = {
    ByteString(hash.sha512(data.toArray))
  }
}
