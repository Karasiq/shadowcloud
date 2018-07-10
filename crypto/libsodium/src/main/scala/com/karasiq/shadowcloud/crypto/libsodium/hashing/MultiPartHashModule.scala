package com.karasiq.shadowcloud.crypto.libsodium.hashing

import akka.util.ByteString
import org.abstractj.kalium.crypto.Hash

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.model.crypto.HashingMethod

private[libsodium] object MultiPartHashModule {
  def SHA256(method: HashingMethod = HashingMethod("SHA256")): MultiPartHashModule = {
    new MultiPartHashModule(method, _.sha256())
  }

  def SHA512(method: HashingMethod = HashingMethod("SHA512")): MultiPartHashModule = {
    new MultiPartHashModule(method, _.sha512())
  }
}

private[libsodium] final class MultiPartHashModule(val method: HashingMethod, newInstance: Hash ⇒ Hash.MultiPartHash)
  extends OnlyStreamHashingModule {

  def createStreamer(): HashingModuleStreamer = {
    new MultiPartHashStreamer(new Hash(), newInstance)
  }

  protected class MultiPartHashStreamer(hashInstance: Hash, newInstance: Hash ⇒ Hash.MultiPartHash) extends HashingModuleStreamer {
    private[this] var hasher: Hash.MultiPartHash = _

    this.reset()

    def module: HashingModule = {
      MultiPartHashModule.this
    }

    def update(data: ByteString): Unit = {
      requireInitialized()
      hasher.update(data.toArray)
    }

    def finish(): ByteString = {
      requireInitialized()
      val outArray = hasher.done()
      ByteString.fromArrayUnsafe(outArray)
    }

    def reset(): Unit = {
      hasher = newInstance(hashInstance)
      hasher.init()
    }

    private[this] def requireInitialized(): Unit = {
      require(hasher ne null, "Not initialized")
    }
  }
}
