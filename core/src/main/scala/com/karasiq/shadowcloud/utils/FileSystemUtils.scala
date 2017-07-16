package com.karasiq.shadowcloud.utils

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import scala.language.postfixOps

private[shadowcloud] object FileSystemUtils {
  def listSubItems(folder: Path, includeFiles: Boolean = true, includeDirs: Boolean = true): Vector[Path] = concurrent.blocking {
    if (!Files.exists(folder)) return Vector.empty
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
  }

  def getFolderSize(folder: Path): Long = concurrent.blocking {
    if (!Files.exists(folder)) return 0L
    var result = 0L
    Files.walkFileTree(folder, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        result += Files.size(file)
        FileVisitResult.CONTINUE
      }

      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (attrs.isSymbolicLink) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
      }
    })
    result
  }
}
