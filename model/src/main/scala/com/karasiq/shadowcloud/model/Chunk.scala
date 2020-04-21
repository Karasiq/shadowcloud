package com.karasiq.shadowcloud.model



import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData, HasWithoutKeys}
import com.karasiq.shadowcloud.model.crypto.EncryptionParameters

@SerialVersionUID(0L)
final case class Chunk(checksum: Checksum = Checksum.empty,
                       encryption: EncryptionParameters = EncryptionParameters.empty,
                       data: Data = Data.empty) extends SCEntity with HasEmpty with HasWithoutData with HasWithoutKeys {
  type Repr = Chunk
  
  def isEmpty: Boolean = {
    data.isEmpty
  }

  def withoutData: Chunk = {
    copy(data = Data.empty)
  }

  def withoutKeys = {
    copy(encryption = encryption.withoutKeys)
  }

  override def hashCode(): Int = {
    checksum.hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case ch: Chunk ⇒
      def hasSameEncryption(ch1: Chunk, ch2: Chunk) =
        if (EncryptionParameters.hasKeys(ch1.encryption) && EncryptionParameters.hasKeys(ch2.encryption)) ch1.encryption == ch2.encryption
        else ch1.encryption.method == ch2.encryption.method

      checksum == ch.checksum && hasSameEncryption(this, ch)

    case _ ⇒ false
  }

  override def toString: String = {
    s"Chunk($checksum, $encryption, ${MemorySize(data.plain.length)}/${MemorySize(data.encrypted.length)})"
  }
}
