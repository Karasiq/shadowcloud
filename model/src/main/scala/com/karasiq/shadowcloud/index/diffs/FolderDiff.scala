package com.karasiq.shadowcloud.index.diffs

import com.karasiq.shadowcloud.index.utils._
import com.karasiq.shadowcloud.model.{File, Folder, Path, SCEntity}
import com.karasiq.shadowcloud.utils.MergeUtil.SplitDecider
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}

@SerialVersionUID(0L)
final case class FolderDiff(
    path: Path,
    time: Long = 0,
    newFiles: Set[File] = Set.empty,
    deletedFiles: Set[File] = Set.empty,
    newFolders: Set[String] = Set.empty,
    deletedFolders: Set[String] = Set.empty
) extends SCEntity
    with HasPath
    with MergeableDiff
    with HasEmpty
    with HasWithoutData {

  type Repr = FolderDiff

  def mergeWith(
      diff: FolderDiff,
      diffDecider: FolderDiffDecider = FolderDiffDecider.rightWins,
      folderDecider: FolderDecider = FolderDecider.mutualExclude
  ): FolderDiff = {
    def unifyPaths(files: Set[File]): Set[File] = files.map(f ⇒ f.copy(path = f.path.withParent(this.path)))

    require(diff.path == path, "Invalid path")
    val newTimestamp = math.max(time, diff.time)

    val (newFiles, deletedFiles) =
      diffDecider.files(this.newFiles, this.deletedFiles, diff.newFiles, diff.deletedFiles, folderDecider.files)

    val (newFolders, deletedFolders) =
      diffDecider.folders(this.newFolders, this.deletedFolders, diff.newFolders, diff.deletedFolders, folderDecider.folders)

    copy(path, newTimestamp, unifyPaths(newFiles), unifyPaths(deletedFiles), newFolders, deletedFolders)
  }

  def diffWith(
      oldDiff: FolderDiff,
      diffDecider: FolderDiffDecider = FolderDiffDecider.idempotent,
      folderDecider: FolderDecider = FolderDecider.mutualExclude
  ): FolderDiff = {
    require(oldDiff.path == path, "Invalid path")
    mergeWith(oldDiff.reverse, diffDecider, folderDecider)
  }

  def merge(right: FolderDiff): FolderDiff = {
    mergeWith(right)
  }

  def diff(right: FolderDiff): FolderDiff = {
    diffWith(right)
  }

  def reverse: FolderDiff = {
    copy(path, time, deletedFiles, newFiles, deletedFolders, newFolders)
  }

  def deletes: FolderDiff = {
    copy(newFiles = Set.empty, newFolders = Set.empty)
  }

  def creates: FolderDiff = {
    copy(deletedFiles = Set.empty, deletedFolders = Set.empty)
  }

  def isEmpty: Boolean = {
    newFiles.isEmpty && deletedFiles.isEmpty && newFolders.isEmpty && deletedFolders.isEmpty
  }

  def withoutData: FolderDiff = {
    copy(newFiles = newFiles.map(_.withoutData), deletedFiles = deletedFiles.map(_.withoutData))
  }

  override def toString: String = {
    if (nonEmpty) {
      s"FolderDiff($path, $time, new files = [${Utils.printValues(newFiles, 5)}], deleted files = [${Utils.printValues(deletedFiles, 10)}], new folders = [${Utils
        .printValues(newFolders, 10)}], deleted folders = [${Utils.printValues(deletedFolders, 10)}])"
    } else {
      s"FolderDiff.empty($path)"
    }
  }
}

object FolderDiff {
  def apply(oldFolder: Folder, newFolder: Folder): FolderDiff = {
    require(oldFolder.path == newFolder.path, "Invalid path")
    val (leftFiles, rightFiles)     = MergeUtil.splitSets(oldFolder.files, newFolder.files, SplitDecider.dropDuplicates)
    val (leftFolders, rightFolders) = MergeUtil.splitSets(oldFolder.folders, newFolder.folders, SplitDecider.dropDuplicates)
    FolderDiff(oldFolder.path, newFolder.timestamp.lastModified, rightFiles, leftFiles, rightFolders, leftFolders)
  }

  def auto(folder1: Folder, folder2: Folder): FolderDiff = {
    val (left, right) = if (folder1.timestamp.lastModified >= folder2.timestamp.lastModified) (folder1, folder2) else (folder2, folder1)
    apply(left, right)
  }

  def create(folder: Folder): FolderDiff = {
    FolderDiff(folder.path, folder.timestamp.lastModified, folder.files, Set.empty, folder.folders, Set.empty)
  }
}
