package com.karasiq.shadowcloud.index

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class Checksum(method: HashingMethod = HashingMethod.default, size: Long = 0, hash: ByteString = ByteString.empty, encryptedSize: Long = 0, encryptedHash: ByteString = ByteString.empty) {
  override def toString = {
    def sizeAndHash(prefix: String, size: Long, hash: ByteString) = {
      if (size == 0 || hash.isEmpty) ""
      else if (size != 0 && hash.isEmpty) s"$prefix: $size bytes"
      else s"$prefix: $size bytes [${Utils.toHexString(hash)}]"
    }
    val plain = sizeAndHash("plain", size, hash)
    val encrypted = sizeAndHash("encrypted", encryptedSize, encryptedHash)
    s"Checksum(${Seq(method.toString, plain, encrypted).filter(_.nonEmpty).mkString(", ")})"
  }
}

object Checksum {
  val empty = Checksum()
}