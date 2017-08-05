package com.karasiq.shadowcloud.crypto.index

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{SignModule, SignParameters}
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData
import com.karasiq.shadowcloud.utils.UUIDUtils

private[shadowcloud] object IndexSignatures {
  private[this] def createPayload(data: EncryptedIndexData, header: EncryptedIndexData.Header): ByteString = {
    UUIDUtils.uuidToBytes(data.id) ++ header.nonce ++ header.data ++ data.data
  }

  def sign(data: EncryptedIndexData, header: EncryptedIndexData.Header,
           signModule: SignModule, signParameters: SignParameters): EncryptedIndexData.Header = {
    val payload = createPayload(data, header)
    val signature = signModule.sign(payload, signParameters)
    header.copy(signature = signature)
  }

  def verify(data: EncryptedIndexData, header: EncryptedIndexData.Header,
             signModule: SignModule, signParameters: SignParameters): Boolean = {
    val payload = createPayload(data, header)
    signModule.verify(payload, header.signature, signParameters)
  }
}
