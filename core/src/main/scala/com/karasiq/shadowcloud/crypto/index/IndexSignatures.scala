package com.karasiq.shadowcloud.crypto.index

import akka.util.ByteString

import com.karasiq.common.encoding.UUIDEncoding
import com.karasiq.shadowcloud.crypto.SignModule
import com.karasiq.shadowcloud.crypto.index.IndexSignatures.{HeaderT, PayloadT}
import com.karasiq.shadowcloud.model.crypto.{SignMethod, SignParameters}
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData

private[shadowcloud] trait IndexSignatures {
  def sign(data: PayloadT, header: HeaderT, signParameters: SignParameters): HeaderT
  def verify(data: PayloadT, header: HeaderT, signParameters: SignParameters): Boolean
}

private[shadowcloud] object IndexSignatures {
  type PayloadT = EncryptedIndexData
  type HeaderT  = EncryptedIndexData.Header

  def createPayload(data: EncryptedIndexData, header: EncryptedIndexData.Header): ByteString = {
    UUIDEncoding.toBytes(data.id) ++ header.nonce ++ header.data ++ data.data
  }

  def apply(signModule: SignModule): IndexSignatures = {
    new DefaultIndexSignatures(signModule)
  }

  def apply(modules: CryptoModuleRegistry, signMethod: SignMethod): IndexSignatures = {
    apply(modules.signModule(signMethod))
  }
}

private[shadowcloud] final class DefaultIndexSignatures(signModule: SignModule) extends IndexSignatures {
  def sign(data: PayloadT, header: HeaderT, signParameters: SignParameters): HeaderT = {
    val payload   = IndexSignatures.createPayload(data, header)
    val signature = signModule.sign(payload, signParameters)
    header.copy(signature = signature)
  }

  def verify(data: PayloadT, header: HeaderT, signParameters: SignParameters): Boolean = {
    val payload = IndexSignatures.createPayload(data, header)
    signModule.verify(payload, header.signature, signParameters)
  }
}
