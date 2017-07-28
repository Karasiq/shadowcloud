package com.karasiq.shadowcloud.utils

import java.nio.ByteOrder
import java.util.UUID

import akka.util.ByteString

private[shadowcloud] object UUIDUtils {
  def uuidToBytes(uuid: UUID): ByteString = {
    implicit val byteOrder: ByteOrder = ByteOrder.BIG_ENDIAN
    val builder = ByteString.newBuilder
    builder.sizeHint(16)
    builder.putLong(uuid.getMostSignificantBits)
      .putLong(uuid.getLeastSignificantBits)
      .result()
  }

  def bytesToUUID(bytes: ByteString): UUID = {
    val bb = bytes.toByteBuffer
    new UUID(bb.getLong, bb.getLong)
  }
}
