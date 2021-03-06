package com.karasiq.shadowcloud.index.files

import com.karasiq.shadowcloud.model.{File, FileId}

import scala.collection.GenTraversableOnce

object FileVersions {
  def mostRecent(files: GenTraversableOnce[File]): File = {
    requireNonEmpty(files)
    files.maxBy(f ⇒ (f.revision, f.timestamp))
  }

  def withId(id: FileId, files: GenTraversableOnce[File]): File = {
    requireNonEmpty(files)
    files.find(_.id == id).getOrElse(throw new NoSuchElementException(s"File not found: $id"))
  }

  def toFlatDirectory(files: Traversable[File]): Seq[File] = {
    files.groupBy(_.path.name).mapValues(mostRecent).values.toVector
  }

  private[this] def requireNonEmpty(files: GenTraversableOnce[File]): Unit = {
    if (files.isEmpty) throw new NoSuchElementException("File not found")
  }
}
