package com.karasiq.shadowcloud.index

import akka.util.ByteString
import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps

case class File(path: Path, name: String, size: Long, created: Long, lastModified: Long, chunks: Seq[ByteString] = Nil) {
  require(size == 0 || chunks.nonEmpty)

  override def toString = {
    val hashesStr = chunks.take(20).map(hash ⇒ Hex.encodeHexString(hash.toArray)).mkString(", ")
    val cutHashesStr = if (chunks.size > 20) hashesStr + ", ..." else hashesStr
    s"File(${path / name}, $size bytes, chunks: [$cutHashesStr])"
  }
}
