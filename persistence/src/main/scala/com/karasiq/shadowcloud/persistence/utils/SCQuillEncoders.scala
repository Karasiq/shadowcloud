package com.karasiq.shadowcloud.persistence.utils

import akka.util.ByteString
import io.getquill.MappedEncoding

import com.karasiq.shadowcloud.utils.ByteStringUnsafe

private[persistence] trait SCQuillEncoders {
  implicit val encodeByteString = MappedEncoding[ByteString, Array[Byte]](ByteStringUnsafe.getArray)
  implicit val decodeByteString = MappedEncoding[Array[Byte], ByteString](ByteString.fromArrayUnsafe)
}
