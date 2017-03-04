package com.karasiq.shadowcloud.crypto

import akka.util.ByteString

import scala.language.postfixOps

trait HashingModule {
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