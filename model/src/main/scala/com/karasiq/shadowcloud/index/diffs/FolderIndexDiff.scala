package com.karasiq.shadowcloud.index.diffs

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.FolderIndex
import com.karasiq.shadowcloud.index.utils.{FolderDecider, HasEmpty, HasWithoutData, MergeableDiff}
import com.karasiq.shadowcloud.model.{File, Folder, Path}
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}
import com.karasiq.shadowcloud.utils.MergeUtil.Decider
import com.karasiq.shadowcloud.utils.MergeUtil.State.{Conflict, Equal, Left, Right}

@SerialVersionUID(0L)
final case class FolderIndexDiff(folders: Seq[FolderDiff] = Vector.empty)
  extends MergeableDiff with HasEmpty with HasWithoutData {

  type Repr = FolderIndexDiff

  // Delete wins by default
  def mergeWith(diff: FolderIndexDiff, folderDecider: FolderDecider = FolderDecider.mutualExclude): FolderIndexDiff = {
    val folders = MergeUtil.mergeByKey[Path, FolderDiff](this.folders, diff.folders, _.path, {
      case Conflict(left, right) ⇒
        Some(left.mergeWith(right, folderDecider)).filter(_.nonEmpty)
    })
    withFolders(folders)
  }

  def diffWith(diff: FolderIndexDiff, decider: Decider[FolderDiff] = Decider.diff,
               folderDecider: FolderDecider = FolderDecider.mutualExclude): FolderIndexDiff = {
    val decider1: Decider[FolderDiff] = decider.orElse {
      case Conflict(left, right) ⇒
        Some(right.diffWith(left, folderDecider)).filter(_.nonEmpty)
    }
    val folders = MergeUtil.mergeByKey[Path, FolderDiff](this.folders, diff.folders, _.path, decider1)
    withFolders(folders)
  }


  def merge(right: FolderIndexDiff): FolderIndexDiff = {
    mergeWith(right)
  }

  def diff(right: FolderIndexDiff): FolderIndexDiff = {
    diffWith(right)
  }

  def creates: FolderIndexDiff = {
    withFolders(folders.map(_.creates))
  }

  def deletes: FolderIndexDiff = {
    withFolders(folders.map(_.deletes))
  }

  def reverse: FolderIndexDiff = {
    withFolders(folders.map(_.reverse))
  }

  def isEmpty: Boolean = {
    folders.isEmpty
  }

  def withoutData: FolderIndexDiff = {
    withFolders(folders.map(_.withoutData))
  }

  override def toString: String = {
    if (isEmpty) "FolderIndexDiff.empty" else s"FolderIndexDiff(${Utils.printValues(folders, 5)})"
  }

  private[this] def withFolders(folders: Seq[FolderDiff]): FolderIndexDiff = {
    if (folders.isEmpty) FolderIndexDiff.empty else copy(folders)
  }
}

object FolderIndexDiff {
  val empty = FolderIndexDiff()

  def seq(diffs: FolderDiff*): FolderIndexDiff = {
    FolderIndexDiff(diffs)
  }

  def apply(left: FolderIndex, right: FolderIndex): FolderIndexDiff = {
    val diffs = MergeUtil.compareMaps(left.folders, right.folders).values.flatMap {
      case Left(folder) ⇒
        Iterator.single(FolderDiff.create(folder))

      case Right(folder) ⇒
        Iterator.single(FolderDiff.create(folder))

      case Conflict(left, right) ⇒
        Iterator.single(right.diff(left))

      case Equal(_) ⇒
        Iterator.empty
    }
    if (diffs.isEmpty) empty else FolderIndexDiff(diffs.toVector)
  }

  def wrap(folderIndex: FolderIndex): FolderIndexDiff = {
    if (folderIndex.folders.isEmpty) {
      empty
    } else {
      FolderIndexDiff(folderIndex.folders.values.map(FolderDiff.create).toVector)
    }
  }

  def create(folders: Folder*): FolderIndexDiff = {
    if (folders.isEmpty) empty else FolderIndexDiff(folders.map(FolderDiff.create))
  }

  def createFiles(files: File*): FolderIndexDiff = {
    if (files.isEmpty) {
      empty
    } else {
      val timestamp = Utils.timestamp
      val diffs = files
        .groupBy(_.path.parent)
        .map { case (path, files) ⇒ FolderDiff(path, timestamp, files.toSet) }
      FolderIndexDiff(diffs.toVector)
    }
  }

  def deleteFolderPaths(folders: Path*): FolderIndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      val timestamp = Utils.timestamp
      val diffs = folders
        .filterNot(_.isRoot)
        .groupBy(_.parent)
        .map { case (parent, folders) ⇒ FolderDiff(parent, timestamp, deletedFolders = folders.map(_.name).toSet) }
      FolderIndexDiff(diffs.toVector)
    }
  }

  // Explicitly deletes sub-items
  def deleteFolders(folders: Folder*): FolderIndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      val timestamp = Utils.timestamp
      val diffs = folders.flatMap { folder ⇒
        Seq(
          FolderDiff(folder.path, timestamp, deletedFiles = folder.files, deletedFolders = folder.folders),
          FolderDiff(folder.path.parent, timestamp, deletedFolders = Set(folder.path.name))
        )
      }
      FolderIndexDiff(diffs.toVector)
    }
  }

  // Explicitly deletes directory tree
  def deleteFolderTree(index: FolderIndex, path: Path): FolderIndexDiff = {
    val folders = index.get(path).toSeq
      .flatMap(f ⇒ f +: f.folders.toSeq.map(f.path / _).flatMap(index.get))
    deleteFolders(folders:_*)
  }

  def deleteFiles(files: File*): FolderIndexDiff = {
    if (files.isEmpty) {
      empty
    } else {
      val timestamp = Utils.timestamp
      val diffs = files
        .groupBy(_.path.parent)
        .map { case (parent, files) ⇒ FolderDiff(parent, timestamp, deletedFiles = files.toSet) }
      FolderIndexDiff(diffs.toVector)
    }
  }

  def move(folder: Folder, newPath: Path): FolderIndexDiff = {
    require(!folder.path.isRoot, "Cannot move root")
    val timestamp = Utils.timestamp
    val diffs = if (newPath.isRoot) {
      Seq(FolderDiff.create(folder.withPath(newPath)))
    } else {
      Seq(
        FolderDiff(newPath.parent, timestamp, newFolders = Set(newPath.name)),
        FolderDiff.create(folder.withPath(newPath))
      )
    }

    deleteFolderPaths(folder.path)
      .mergeWith(FolderIndexDiff(diffs), FolderDecider.createWins)
  }
}