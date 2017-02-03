package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.index.{ChunkIndexDiff, FolderDiff, Path}

import scala.collection.mutable
import scala.language.postfixOps

case class IndexDiff(time: Long = 0, folders: Seq[FolderDiff] = Nil, chunks: ChunkIndexDiff = ChunkIndexDiff.empty) {
  def merge(diff: IndexDiff): IndexDiff = {
    val (first, second) = if (this.time > diff.time) (diff, this) else (this, diff)
    val folders = mutable.AnyRefMap[Path, FolderDiff]()
    folders ++= first.folders.map(f ⇒ (f.path, f))
    second.folders.foreach { folder ⇒
      val existing = folders.get(folder.path)
      folders += folder.path → existing.fold(folder)(_.merge(folder))
    }
    IndexDiff(
      second.time,
      folders.values.toSeq,
      first.chunks.merge(second.chunks)
    )
  }
}

object IndexDiff {
  val empty = IndexDiff()
}
