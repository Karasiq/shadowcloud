package com.karasiq.shadowcloud.crypto.internal

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule, HashingModuleStreamer, StreamHashingModule}

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
