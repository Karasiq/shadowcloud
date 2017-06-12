package com.karasiq.shadowcloud.index.diffs

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

import com.karasiq.shadowcloud.index.File

object FileVersions {
  def mostRecent(files: GenTraversableOnce[File]): File = {
    if (files.isEmpty) throw new NoSuchElementException("File not found")
    files.maxBy(f ⇒ (f.revision, f.timestamp.lastModified))
  }
}