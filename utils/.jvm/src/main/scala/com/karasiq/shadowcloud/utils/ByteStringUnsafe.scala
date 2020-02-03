package com.karasiq.shadowcloud.utils

import akka.util.ByteString

import scala.util.Try

object ByteStringUnsafe {
  private[this] val byteString1C = Try(Class.forName("akka.util.ByteString$ByteString1C"))
  private[this] val bytesField = byteString1C.map(_.getDeclaredField("bytes"))
  bytesField.foreach(_.setAccessible(true))

  def getArray(bs: ByteString): Array[Byte] = {
    if (byteString1C.isSuccess && bytesField.isSuccess && byteString1C.get.isInstance(bs)) {
      bytesField.get.get(bs) match {
        case bs: Array[Byte] => bs
        case _ => bs.toArray // Patched version
      }
    } else {
      bs.toArray
    }
  }

  object implicits {

    implicit class ImplicitByteStringUnsafeOps(private val bs: ByteString) extends AnyVal {
      def toArrayUnsafe: Array[Byte] = getArray(bs)
    }

  }

}
