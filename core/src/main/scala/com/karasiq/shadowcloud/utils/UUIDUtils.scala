package com.karasiq.shadowcloud.utils

import java.nio.ByteOrder
import java.util.UUID

import akka.util.ByteString

private[shadowcloud] object UUIDUtils {
  def uuidToBytes(uuid: UUID): ByteString = {
    implicit val byteOrder = ByteOrder.BIG_ENDIAN
    ByteString.newBuilder
      .putLong(uuid.getMostSignificantBits)
      .putLong(uuid.getLeastSignificantBits)
      .result()
  }

  def bytesToUUID(bytes: ByteString): UUID = {
    val bb = bytes.toByteBuffer
    new UUID(bb.getLong, bb.getLong)
  }
}
