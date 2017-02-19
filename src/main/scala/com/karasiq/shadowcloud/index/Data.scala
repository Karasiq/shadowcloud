package com.karasiq.shadowcloud.index

import akka.util.ByteString
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData}

import scala.language.postfixOps

case class Data(plain: ByteString = ByteString.empty, encrypted: ByteString = ByteString.empty)
  extends HasEmpty with HasWithoutData {

  type Repr = Data

  def isEmpty: Boolean = {
    plain.isEmpty && encrypted.isEmpty
  }

  def withoutData: Data = {
    Data.empty
  }
}

object Data {
  val empty = Data()
}
