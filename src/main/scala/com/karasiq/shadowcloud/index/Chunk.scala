package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.crypto.EncryptionParameters
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData}
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class Chunk(checksum: Checksum = Checksum.empty, encryption: EncryptionParameters = EncryptionParameters.empty,
                 data: Data = Data.empty) extends HasEmpty with HasWithoutData {
  type Repr = Chunk
  
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
    s"Chunk($checksum, $encryption, ${Utils.printSize(data.plain.length)}/${Utils.printSize(data.encrypted.length)})"
  }
}