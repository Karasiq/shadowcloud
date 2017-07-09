package com.karasiq.shadowcloud.utils

import java.io.OutputStream

import akka.util.ByteString

object ByteStringOutputStream {
  def apply(): ByteStringOutputStream = {
    new ByteStringOutputStream()
  }
}

class ByteStringOutputStream extends OutputStream {
  private[this] var buffer = ByteString.empty

  def write(b: Int): Unit = {
    buffer :+= b.toByte
  }

  override def write(b: Array[Byte]): Unit = {
    buffer ++= ByteString(b)
  }

  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    buffer ++= ByteString.fromArray(b, off, len)
  }

  def toByteString: ByteString = {
    buffer
  }
}
