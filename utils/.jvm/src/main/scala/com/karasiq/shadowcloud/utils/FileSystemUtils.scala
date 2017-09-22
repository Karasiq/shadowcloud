package com.karasiq.shadowcloud.utils

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import scala.collection.mutable.{Set ⇒ MSet}
import scala.collection.JavaConverters._
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{ActorAttributes, Attributes}
import akka.stream.scaladsl.Source

private[shadowcloud] object FileSystemUtils {
  private[this] val forbiddenChars = """[<>:"/\\|?*]""".r

  def walkFileTree(folder: Path,
                   walkUntil: Path ⇒ Boolean = _ ⇒ true,
                   includeFiles: Boolean = true,
                   includeDirs: Boolean = true,
                   maxDepth: Int = Int.MaxValue,
                   options: Set[FileVisitOption] = Set.empty): Source[Path, NotUsed] = {
    Source.single(folder)
      .mapConcat { path ⇒
        val builder = List.newBuilder[Path]
        val visited = MSet.empty[Path]
        Files.walkFileTree(path, options.asJava, maxDepth, new SimpleFileVisitor[Path] {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            if (includeFiles) builder += file
            FileVisitResult.CONTINUE
          }

          override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
            if (visited.contains(dir)) {
              FileVisitResult.SKIP_SUBTREE
            } else {
              visited += dir
              if (includeDirs) builder += dir
              if (walkUntil(dir)) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
            }
          }

          override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
            FileVisitResult.CONTINUE
          }

          override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
            FileVisitResult.CONTINUE
          }
        })
        builder.result()
      }
      .withAttributes(Attributes.name("walkFileTree") and ActorAttributes.IODispatcher)
  }

  def listSubItems(folder: Path, includeFiles: Boolean = true, includeDirs: Boolean = true): Source[Path, NotUsed] = {
    walkFileTree(folder, _ == folder, includeFiles, includeDirs).named("listSubItems")
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

  def isValidFileName(fileName: String): Boolean = {
    forbiddenChars.findFirstIn(fileName).isEmpty
  }
}
