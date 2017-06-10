package com.karasiq.shadowcloud.persistence.utils

import akka.util.ByteString
import io.getquill.MappedEncoding

private[persistence] trait SCQuillEncoders {
  implicit val encodeByteString = MappedEncoding[ByteString, Array[Byte]](_.toArray)
  implicit val decodeByteString = MappedEncoding[Array[Byte], ByteString](ByteString.fromArray)
}
