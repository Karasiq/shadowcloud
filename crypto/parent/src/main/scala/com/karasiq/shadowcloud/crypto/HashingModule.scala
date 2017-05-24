package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

trait HashingModule extends CryptoModule {
  def method: HashingMethod
  def createHash(data: ByteString): ByteString
}

trait StreamHashingModule extends HashingModule {
  def update(data: ByteString): Unit
  def createHash(): ByteString
  def reset(): Unit

  // One pass function
  override def createHash(data: ByteString): ByteString = {
    update(data)
    val hash = createHash()
    reset()
    hash
  }
}