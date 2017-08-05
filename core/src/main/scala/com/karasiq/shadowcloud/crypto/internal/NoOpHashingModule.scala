package com.karasiq.shadowcloud.crypto.internal

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}

private[crypto] final class NoOpHashingModule extends StreamHashingModule {
  def method: HashingMethod = HashingMethod.none
  def update(data: ByteString): Unit = ()
  def createHash(): ByteString = ByteString.empty
  def reset(): Unit = ()
}
