package com.karasiq.shadowcloud.crypto.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule}

import scala.language.postfixOps

private[crypto] final class NoOpHashingModule extends HashingModule {
  def method: HashingMethod = HashingMethod.none
  def update(data: ByteString): Unit = ()
  def createHash(): ByteString = ByteString.empty
  def reset(): Unit = ()
}
