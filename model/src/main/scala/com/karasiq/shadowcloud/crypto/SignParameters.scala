package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

case class SignParameters(method: SignMethod, publicKey: ByteString, privateKey: ByteString) {
  override def toString: String = {
    s"SignParameters($method)"
  }
}

object SignParameters {
  val empty = SignParameters(SignMethod.none, ByteString.empty, ByteString.empty)
}