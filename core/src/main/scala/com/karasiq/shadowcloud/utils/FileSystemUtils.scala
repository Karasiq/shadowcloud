package com.karasiq.shadowcloud.utils

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import scala.language.postfixOps

private[shadowcloud] object FileSystemUtils {
  def listSubItems(folder: Path, includeFiles: Boolean = true, includeDirs: Boolean = true): Vector[Path] = concurrent.blocking {
    try {
      val builder = Vector.newBuilder[Path]
      Files.walkFileTree(folder, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (includeFiles) builder += file
          FileVisitResult.CONTINUE
        }

        override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (includeDirs) builder += dir
          if (dir == folder) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
        }
      })
      builder.result()
    } catch {
      case _: NoSuchFileException ⇒
        Vector.empty
    }
  }
}
