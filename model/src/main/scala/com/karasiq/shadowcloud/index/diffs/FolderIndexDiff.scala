package com.karasiq.shadowcloud.index.diffs

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.FolderIndex
import com.karasiq.shadowcloud.index.utils.{FolderDecider, HasEmpty, HasWithoutData, MergeableDiff}
import com.karasiq.shadowcloud.model.{File, Folder, Path, SCEntity}
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}
import com.karasiq.shadowcloud.utils.MergeUtil.Decider
import com.karasiq.shadowcloud.utils.MergeUtil.State.{Conflict, Equal, Left, Right}

@SerialVersionUID(0L)
final case class FolderIndexDiff(folders: Seq[FolderDiff] = Vector.empty)
  extends SCEntity with MergeableDiff with HasEmpty with HasWithoutData {

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
    withFolders(dropDeleted(folders).map(_.creates))
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
    val nonEmptyDiffs = folders.filter(_.nonEmpty).toVector
    if (nonEmptyDiffs.isEmpty) FolderIndexDiff.empty else copy(nonEmptyDiffs)
  }

  private[this] def dropDeleted(folders: Seq[FolderDiff]): Seq[FolderDiff] = {
    val deleted = folders.flatMap(fd ⇒ fd.deletedFolders.map(fd.path / _))
    folders.filterNot(fd ⇒ deleted.exists(fd.path.startsWith))
  }
}

object FolderIndexDiff {
  val empty = FolderIndexDiff()

  def fromDiffs(diffs: FolderDiff*): FolderIndexDiff = {
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

  def equalsIgnoreOrder(diff1: FolderIndexDiff, diff2: FolderIndexDiff): Boolean = {
    diff1.folders.toSet == diff2.folders.toSet
  }

  def createFolders(folders: Folder*): FolderIndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      val diffs = folders.flatMap { folder ⇒
        if (folder.path.isRoot) {
          Seq(FolderDiff.create(folder))
        } else {
          Seq(
            FolderDiff(folder.path.parent, folder.timestamp.created, newFolders = Set(folder.path.name)),
            FolderDiff.create(folder)
          )
        }
      }
      FolderIndexDiff(diffs)
    }
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

  // Explicitly deletes folders
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

  // Explicitly deletes folder items
  def deleteFolderItems(index: FolderIndex, path: Path): FolderIndexDiff = {
    val folders = index.get(path).toSeq
      .flatMap(f ⇒ f +: f.folders.toSeq.map(f.path / _).flatMap(index.get))
    deleteFolders(folders:_*)
  }

  // Explicitly deletes folder tree
  def deleteFolderTree(index: FolderIndex, path: Path): FolderIndexDiff = {
    val folders = FolderIndex.traverseFolderTree(index, path)
    deleteFolders(folders.toSeq:_*)
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

  def copyFiles(files: Set[File], newPath: Path): FolderIndexDiff = {
    require(!newPath.isRoot, "Invalid file path")
    val diff = FolderDiff(newPath.parent, Utils.timestamp, newFiles = files.map(_.copy(path = newPath)))
    FolderIndexDiff.fromDiffs(diff)
  }

  def copyFile(file: File, newPath: Path): FolderIndexDiff = {
    copyFiles(Set(file), newPath)
  }

  def copyFile(index: FolderIndex, path: Path, newPath: Path): FolderIndexDiff = {
    require(!path.isRoot && !newPath.isRoot, "Invalid file path")
    val newFiles = index.getFiles(path).map(f ⇒ f.copy(path = newPath))
    val diff = FolderDiff(newPath.parent, Utils.timestamp, newFiles = newFiles)
    FolderIndexDiff.fromDiffs(diff)
  }

  def moveFiles(files: Set[File], newPath: Path): FolderIndexDiff = {
    val deleteDiff = deleteFiles(files.toSeq:_*)
    val copyDiff = copyFiles(files, newPath)
    deleteDiff.mergeWith(copyDiff, FolderDecider.createWins)
  }

  def moveFile(file: File, newPath: Path): FolderIndexDiff = {
    moveFiles(Set(file), newPath)
  }

  def moveFile(index: FolderIndex, path: Path, newPath: Path): FolderIndexDiff = {
    val deleteDiff = deleteFiles(index.getFiles(path).toSeq: _*)
    val copyDiff = copyFile(index, path, newPath)
    deleteDiff.mergeWith(copyDiff, FolderDecider.createWins)
  }

  def copyFolder(folder: Folder, newPath: Path): FolderIndexDiff = {
    val newFolder = folder.withPath(newPath)
    createFolders(newFolder)
  }

  def copyFolder(index: FolderIndex, path: Path, newPath: Path): FolderIndexDiff = {
    val folders = FolderIndex.traverseFolderTree(index, path)
    val newFolders = folders.map(f ⇒ f.withPath(newPath / f.path.toRelative(path)))
    createFolders(newFolders.toSeq:_*)
  }

  def moveFolder(folder: Folder, newPath: Path): FolderIndexDiff = {
    require(!folder.path.isRoot, "Cannot move root")
    val deleteDiff = deleteFolderPaths(folder.path)
    val copyDiff = copyFolder(folder, newPath)
    deleteDiff.mergeWith(copyDiff, FolderDecider.createWins)
  }

  def moveFolder(index: FolderIndex, path: Path, newPath: Path): FolderIndexDiff = {
    val copyDiff = copyFolder(index, path, newPath)
    val deleteDiff = deleteFolderPaths(path)
    deleteDiff.mergeWith(copyDiff, FolderDecider.createWins)
  }
}