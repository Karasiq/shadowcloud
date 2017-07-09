package com.karasiq.shadowcloud.utils

import java.io.InputStream

import akka.util.ByteString

object ByteStringInputStream {
  def apply(bytes: ByteString): ByteStringInputStream = {
    new ByteStringInputStream(bytes)
  }
}

class ByteStringInputStream(bytes: ByteString) extends InputStream {
  private[this] var buffer = bytes

  def read(): Int = {
    if (endReached) return -1
    val byte = buffer.head
    buffer = buffer.drop(1)
    byte
  }

  override def read(b: Array[Byte]): Int = {
    read(b, 0, b.length)
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    if (endReached) return -1
    val (drop, keep) = buffer.splitAt(len)
    drop.copyToArray(b, off, len)
    buffer = keep
    drop.length
  }

  override def available(): Int = {
    buffer.length
  }

  override def skip(n: Long): Long = {
    val length = math.min(buffer.length, math.min(n, Int.MaxValue).toInt)
    buffer = buffer.drop(length)
    length
  }

  private[this] def endReached: Boolean = {
    buffer.isEmpty
  }
}
