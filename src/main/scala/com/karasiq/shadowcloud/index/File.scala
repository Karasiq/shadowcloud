package com.karasiq.shadowcloud.index

import org.apache.commons.codec.binary.Hex

import scala.language.postfixOps

case class File(parent: Path, name: String, created: Long = 0, lastModified: Long = 0, checksum: Checksum = Checksum.empty, chunks: Seq[Chunk] = Nil) extends HasPath {
  require(name.nonEmpty)
  def path: Path = parent / name

  override def hashCode(): Int = {
    (parent, name, chunks).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: File ⇒
      f.parent == parent && f.name == name && f.chunks == chunks
  }

  override def toString: String = {
    val hashesStr = chunks.take(20).map(chunk ⇒ Hex.encodeHexString(chunk.checksum.hash.toArray)).mkString(", ")
    val cutHashesStr = if (chunks.size > 20) hashesStr + ", ..." else hashesStr
    s"File(${parent / name}, $checksum, chunks: [$cutHashesStr])"
  }
}
