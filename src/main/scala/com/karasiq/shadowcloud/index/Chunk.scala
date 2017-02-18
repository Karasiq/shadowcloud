package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.crypto.EncryptionParameters
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData}

import scala.language.postfixOps

case class Chunk(checksum: Checksum = Checksum.empty, encryption: EncryptionParameters = EncryptionParameters.empty,
                 data: Data = Data.empty) extends HasEmpty with HasWithoutData[Chunk] {
  def isEmpty: Boolean = {
    data.isEmpty
  }

  def withoutData: Chunk = {
    copy(data = Data.empty)
  }
  
  override def hashCode(): Int = {
    (checksum, encryption).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case ch: Chunk ⇒
      checksum == ch.checksum && encryption == ch.encryption

    case _ ⇒
      false
  }

  override def toString: String = {
    s"Chunk($checksum, $encryption, ${data.plain.length} / ${data.encrypted.length} bytes)"
  }
}