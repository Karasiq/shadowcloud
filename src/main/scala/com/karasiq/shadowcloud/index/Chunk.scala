package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.crypto.EncryptionParameters

import scala.language.postfixOps

case class Chunk(checksum: Checksum = Checksum.empty, encryption: EncryptionParameters = EncryptionParameters.empty, data: Data = Data.empty) {
  def withoutData = copy(data = Data.empty)

  override def toString = {
    s"Chunk($checksum, $encryption, ${data.plain.length} / ${data.encrypted.length} bytes)"
  }
}