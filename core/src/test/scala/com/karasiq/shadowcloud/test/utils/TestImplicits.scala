package com.karasiq.shadowcloud.test.utils

import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

trait TestImplicits {
  implicit class ByteStringOps(private val bs: ByteString) {
    def toHexString: String = {
      Utils.toHexString(bs)
    }
  }

  implicit class ByteStringObjOps(private val bs: ByteString.type) {
    def fromHexString(hexString: String): ByteString = {
      Utils.parseHexString(hexString)
    }

    def fromChunks(chunks: Seq[Chunk]): ByteString = {
      chunks.map(_.data.plain).fold(ByteString.empty)(_ ++ _)
    }

    def fromEncryptedChunks(chunks: Seq[Chunk]): ByteString = {
      chunks.map(_.data.encrypted).fold(ByteString.empty)(_ ++ _)
    }
  }
}
