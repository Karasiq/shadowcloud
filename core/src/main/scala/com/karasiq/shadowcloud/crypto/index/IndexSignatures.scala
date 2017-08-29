package com.karasiq.shadowcloud.crypto.index

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{SignMethod, SignModule, SignParameters}
import com.karasiq.shadowcloud.crypto.index.IndexSignatures.{Header, Payload}
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.serialization.protobuf.index.EncryptedIndexData
import com.karasiq.shadowcloud.utils.UUIDUtils

private[shadowcloud] trait IndexSignatures {
  def sign(data: Payload, header: Header, signParameters: SignParameters): Header
  def verify(data: Payload, header: Header, signParameters: SignParameters): Boolean
}

private[shadowcloud] object IndexSignatures {
  type Payload = EncryptedIndexData
  type Header = EncryptedIndexData.Header

  def createPayload(data: EncryptedIndexData, header: EncryptedIndexData.Header): ByteString = {
    UUIDUtils.toBytes(data.id) ++ header.nonce ++ header.data ++ data.data
  }

  def apply(signModule: SignModule): IndexSignatures = {
    new DefaultIndexSignatures(signModule)
  }

  def apply(modules: CryptoModuleRegistry, signMethod: SignMethod): IndexSignatures = {
    apply(modules.signModule(signMethod))
  }
}

private[shadowcloud] final class DefaultIndexSignatures(signModule: SignModule) extends IndexSignatures {
  def sign(data: Payload, header: Header, signParameters: SignParameters): Header = {
    val payload = IndexSignatures.createPayload(data, header)
    val signature = signModule.sign(payload, signParameters)
    header.copy(signature = signature)
  }

  def verify(data: Payload, header: Header, signParameters: SignParameters): Boolean = {
    val payload = IndexSignatures.createPayload(data, header)
    signModule.verify(payload, header.signature, signParameters)
  }
}
