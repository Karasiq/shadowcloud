package com.karasiq.shadowcloud.index

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class Checksum(method: HashingMethod = HashingMethod.default, size: Long = 0, hash: ByteString = ByteString.empty,
                    encryptedSize: Long = 0, encryptedHash: ByteString = ByteString.empty) extends HasEmpty {
  def isEmpty: Boolean = {
    size == 0 && hash.isEmpty && encryptedSize == 0 && encryptedHash.isEmpty
  }

  override def hashCode(): Int = {
    (method, size, hash).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case Checksum(method1, size1, hash1, encryptedSize1, encryptedHash1) ⇒
      method == method1 &&
        size == size1 &&
        hash == hash1 &&
        ((encryptedSize == 0 || encryptedSize1 == 0) || encryptedSize == encryptedSize1) &&
        ((encryptedHash.isEmpty || encryptedHash1.isEmpty) || encryptedHash == encryptedHash1)

    case _ ⇒
      false
  }

  override def toString: String = {
    def sizeAndHash(prefix: String, size: Long, hash: ByteString) = {
      if (size == 0 || hash.isEmpty) ""
      else if (size != 0 && hash.isEmpty) s"$prefix: $size bytes"
      else s"$prefix: ${Utils.printSize(size)} [${Utils.toHexString(hash)}]"
    }
    val plain = sizeAndHash("plain", size, hash)
    val encrypted = sizeAndHash("encrypted", encryptedSize, encryptedHash)
    s"Checksum(${Array(method.toString, plain, encrypted).filter(_.nonEmpty).mkString(", ")})"
  }
}

object Checksum {
  val empty = Checksum()
}