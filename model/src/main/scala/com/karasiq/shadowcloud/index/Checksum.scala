package com.karasiq.shadowcloud.index

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.utils.{HexString, MemorySize}

import scala.language.postfixOps

case class Checksum(method: HashingMethod = HashingMethod.default, encMethod: HashingMethod = HashingMethod.default,
                    size: Long = 0, hash: ByteString = ByteString.empty, encryptedSize: Long = 0,
                    encryptedHash: ByteString = ByteString.empty) extends HasEmpty {
  def isEmpty: Boolean = {
    size == 0 && hash.isEmpty && encryptedSize == 0 && encryptedHash.isEmpty
  }

  override def hashCode(): Int = {
    (method, size, hash).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case Checksum(method1, encMethod1, size1, hash1, encryptedSize1, encryptedHash1) ⇒
      method == method1 &&
        size == size1 &&
        hash == hash1 &&
        ((encryptedSize == 0 || encryptedSize1 == 0) || encryptedSize == encryptedSize1) &&
        ((encryptedHash.isEmpty || encryptedHash1.isEmpty) || (encMethod == encMethod1 && encryptedHash == encryptedHash1))

    case _ ⇒
      false
  }

  override def toString: String = {
    def sizeAndHash(prefix: String, size: Long, hash: ByteString) = {
      if (size == 0 && hash.isEmpty) ""
      else if (hash.isEmpty) s"$prefix: ${MemorySize.toString(size)}"
      else s"$prefix: ${MemorySize.toString(size)} [${HexString.encode(hash)}]"
    }
    val methods = if (method == encMethod) {
      Seq(method.toString)
    } else {
      Seq(method.toString, encMethod.toString)
    }
    val plain = sizeAndHash("plain", size, hash)
    val encrypted = sizeAndHash("encrypted", encryptedSize, encryptedHash)

    s"Checksum(${(methods ++ Seq(plain, encrypted)).filter(_.nonEmpty).mkString(", ")})"
  }
}

object Checksum {
  val empty = Checksum()
}