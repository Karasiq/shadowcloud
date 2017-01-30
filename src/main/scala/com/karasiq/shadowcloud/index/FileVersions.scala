package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class FileVersions(files: Map[String, Seq[File]]) {
  def get(file: String, revision: Int): Option[File] = {
    files
      .get(file)
      .filter(_.length > revision)
      .map(_(revision))
  }
}

object FileVersions {
  def apply(folder: Folder): FileVersions = {
    val files = folder.files.groupBy(_.name).mapValues(_.toIndexedSeq.sortBy(_.lastModified))
    FileVersions(files)
  }
}