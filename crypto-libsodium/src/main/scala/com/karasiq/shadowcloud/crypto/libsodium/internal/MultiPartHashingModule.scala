package com.karasiq.shadowcloud.crypto.libsodium.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}
import org.abstractj.kalium.crypto.Hash

private[libsodium] final class MultiPartHashingModule(val method: HashingMethod, newInstance: Hash ⇒ Hash.MultiPartHash) extends StreamHashingModule {
  private[this] val hashInstance = new Hash()
  private[this] var hasher: Hash.MultiPartHash = _
  this.reset()

  def update(data: ByteString): Unit = {
    hasher.update(data.toArray)
  }

  def createHash(): ByteString = {
    ByteString(hasher.done())
  }

  def reset(): Unit = {
    hasher = newInstance(hashInstance)
    hasher.init()
  }
}
