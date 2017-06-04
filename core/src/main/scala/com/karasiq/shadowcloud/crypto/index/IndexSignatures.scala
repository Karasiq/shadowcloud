package com.karasiq.shadowcloud.crypto.index

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{SignModule, SignParameters}
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData
import com.karasiq.shadowcloud.utils.UUIDUtils

private[shadowcloud] object IndexSignatures {
  private[this] def createPayload(data: EncryptedIndexData): ByteString = {
    UUIDUtils.uuidToBytes(data.keysId) ++ data.header ++ data.data
  }

  def sign(data: EncryptedIndexData, signModule: SignModule, signParameters: SignParameters): EncryptedIndexData = {
    val payload = createPayload(data)
    val signature = signModule.sign(payload, signParameters)
    data.copy(signature = signature)
  }

  def verify(data: EncryptedIndexData, signModule: SignModule, signParameters: SignParameters): Boolean = {
    val payload = createPayload(data)
    signModule.verify(payload, data.signature, signParameters)
  }
}
