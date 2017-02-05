package com.karasiq.shadowcloud.utils

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import scala.language.postfixOps

private[shadowcloud] object FileSystemUtils {
  def listFiles(folder: Path): Vector[String] = {
    val files = Vector.newBuilder[String]
    Files.walkFileTree(folder, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        files += file.getFileName.toString
        FileVisitResult.CONTINUE
      }

      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
        if (dir == folder) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
      }
    })
    files.result()
  }
}
