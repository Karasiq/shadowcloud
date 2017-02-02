package com.karasiq.shadowcloud.index

import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps

case class File(path: Path, name: String, created: Long = 0, lastModified: Long = 0, checksum: Checksum = Checksum.empty, chunks: Seq[Chunk] = Nil) {
  override def toString = {
    val hashesStr = chunks.take(20).map(chunk ⇒ Hex.encodeHexString(chunk.checksum.hash.toArray)).mkString(", ")
    val cutHashesStr = if (chunks.size > 20) hashesStr + ", ..." else hashesStr
    s"File(${path / name}, $checksum, chunks: [$cutHashesStr])"
  }
}
