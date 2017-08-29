package com.karasiq.shadowcloud.utils

import java.nio.ByteBuffer
import java.util.UUID

import akka.util.ByteString
import com.google.protobuf.{ByteString ⇒ PBByteString}
import com.trueaccord.scalapb.TypeMapper

object ProtobufUtils {
  private[this] val emptyUUID = new UUID(0, 0)

  implicit val byteStringMapper = TypeMapper[PBByteString, ByteString] { bs ⇒
    if (bs.isEmpty) ByteString.empty else ByteString(bs.toByteArray)
  } { bs ⇒
    if (bs.isEmpty) PBByteString.EMPTY else PBByteString.copyFrom(bs.toArray)
  }
  
  implicit val uuidMapper = TypeMapper[PBByteString, UUID] { bs ⇒
    if (bs.isEmpty) {
      emptyUUID
    } else {
      val bb = ByteBuffer.wrap(bs.toByteArray)
      new UUID(bb.getLong, bb.getLong)
    }
  } { uuid ⇒
    val bb = ByteBuffer.allocate(16)
    bb.putLong(uuid.getMostSignificantBits)
    bb.putLong(uuid.getLeastSignificantBits)
    bb.flip()
    PBByteString.copyFrom(bb.array())
  }
}
