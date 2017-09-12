package com.karasiq.shadowcloud.model

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData}
import com.karasiq.shadowcloud.model.crypto.EncryptionParameters
import com.karasiq.shadowcloud.utils.MemorySize

@SerialVersionUID(0L)
final case class Chunk(checksum: Checksum = Checksum.empty,
                       encryption: EncryptionParameters = EncryptionParameters.empty,
                       data: Data = Data.empty) extends SCEntity with HasEmpty with HasWithoutData {
  type Repr = Chunk
  
  def isEmpty: Boolean = {
    data.isEmpty
  }

  def withoutData: Chunk = {
    copy(data = Data.empty)
  }
  
  override def hashCode(): Int = {
    checksum.hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case ch: Chunk ⇒
      checksum == ch.checksum && encryption == ch.encryption

    case _ ⇒
      false
  }

  override def toString: String = {
    s"Chunk($checksum, $encryption, ${MemorySize(data.plain.length)}/${MemorySize(data.encrypted.length)})"
  }
}