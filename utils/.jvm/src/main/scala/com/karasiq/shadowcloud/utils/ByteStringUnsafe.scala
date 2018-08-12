package com.karasiq.shadowcloud.utils

import scala.util.Try

import akka.util.ByteString

object ByteStringUnsafe {
  private[this] val byteString1C = Try(Class.forName("akka.util.ByteString$ByteString1C"))
  private[this] val bytesField = byteString1C.map(_.getDeclaredField("bytes"))
  bytesField.foreach(_.setAccessible(true))

  def getArray(bs: ByteString): Array[Byte] = {
    if (byteString1C.isSuccess && bytesField.isSuccess && byteString1C.get.isInstance(bs)) {
      bytesField.get.get(bs).asInstanceOf[Array[Byte]]
    } else {
      bs.toArray
    }
  }
}
