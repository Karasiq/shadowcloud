package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class File(parent: Path, name: String, created: Long = 0, lastModified: Long = 0, checksum: Checksum = Checksum.empty, chunks: Seq[Chunk] = Nil) extends HasPath {
  require(lastModified >= created, "Invalid file time")
  require(name.nonEmpty, "File name couldn't be empty")

  def path: Path = parent / name

  def withoutData: File = {
    copy(chunks = chunks.map(_.withoutData))
  }

  override def hashCode(): Int = {
    (parent, name, checksum, chunks).hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: File ⇒
      f.parent == parent && f.name == name && f.checksum == checksum && f.chunks == chunks
  }

  override def toString: String = {
    s"File(${parent / name}, $checksum, chunks: [${Utils.printHashes(chunks)}])"
  }
}
