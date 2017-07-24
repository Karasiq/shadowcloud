package com.karasiq.shadowcloud.index.diffs

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

import com.karasiq.shadowcloud.index.File

object FileVersions {
  def mostRecent(files: GenTraversableOnce[File]): File = {
    requireNonEmpty(files)
    files.maxBy(f ⇒ (f.revision, f.timestamp))
  }

  def withId(id: File.ID, files: GenTraversableOnce[File]): File = {
    requireNonEmpty(files)
    files.find(_.id == id).getOrElse(throw new NoSuchElementException(s"File not found: $id"))
  }

  private[this] def requireNonEmpty(files: GenTraversableOnce[File]): Unit = {
    if (files.isEmpty) throw new NoSuchElementException("File not found")
  }
}