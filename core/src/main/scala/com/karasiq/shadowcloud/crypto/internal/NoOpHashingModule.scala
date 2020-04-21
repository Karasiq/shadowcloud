package com.karasiq.shadowcloud.crypto.internal



import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingModule, HashingModuleStreamer, StreamHashingModule}
import com.karasiq.shadowcloud.model.crypto.HashingMethod

private[crypto] final class NoOpHashingModule extends StreamHashingModule {
  def method: HashingMethod = HashingMethod.none
  def createHash(data: ByteString) = ByteString.empty
  def createStreamer(): HashingModuleStreamer = NoOpHashingStreamer

  private[this] object NoOpHashingStreamer extends HashingModuleStreamer {
    def module: HashingModule = NoOpHashingModule.this
    def update(data: ByteString): Unit = ()
    def reset(): Unit = ()
    def finish(): ByteString = ByteString.empty
  }
}
