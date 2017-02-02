package com.karasiq.shadowcloud.index

import akka.util.ByteString

import scala.language.postfixOps

case class Data(plain: ByteString = ByteString.empty, encrypted: ByteString = ByteString.empty) {
  def nonEmpty: Boolean = plain.nonEmpty || encrypted.nonEmpty
}

object Data {
  val empty = Data()
}
