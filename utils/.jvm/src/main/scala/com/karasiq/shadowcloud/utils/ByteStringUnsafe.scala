package com.karasiq.shadowcloud.utils

import akka.util.ByteString

import scala.util.Try

object ByteStringUnsafe {
  private[this] val ByteString1CRef = Try(Class.forName("akka.util.ByteString$ByteString1C"))
  private[this] val BytesFieldRef = ByteString1CRef.map { cls =>
    val f = cls.getDeclaredField("bytes")
    f.setAccessible(true)
    f
  }

  def getArray(bs: ByteString): Array[Byte] =
    if (BytesFieldRef.isSuccess && ByteString1CRef.get.isInstance(bs)) {
      BytesFieldRef.get.get(bs) match {
        case bs: Array[Byte] => bs
        case _ => bs.toArray // Patched version
      }
    } else bs.toArray

  object implicits {
    implicit class ImplicitByteStringUnsafeOps(private val bs: ByteString) extends AnyVal {
      def toArrayUnsafe: Array[Byte] = getArray(bs)
    }
  }
}
