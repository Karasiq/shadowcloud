package com.karasiq.shadowcloud.index.diffs

import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.utils.FolderDecider
import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.SplitDecider

import scala.language.postfixOps

case class FolderDiff(path: Path, time: Long = 0, newFiles: Set[File] = Set.empty,
                      deletedFiles: Set[File] = Set.empty, newFolders: Set[String] = Set.empty,
                      deletedFolders: Set[String] = Set.empty) extends HasPath {
  def nonEmpty: Boolean = {
    newFiles.nonEmpty || deletedFiles.nonEmpty || newFolders.nonEmpty || deletedFolders.nonEmpty
  }

  def merge(diff: FolderDiff, decider: FolderDecider = FolderDecider.mutualExclude): FolderDiff = {
    require(diff.path == path, "Invalid path")
    val (newFiles, deletedFiles) = MergeUtil.splitSets(this.newFiles ++ diff.newFiles,
      this.deletedFiles ++ diff.deletedFiles, decider.files)
    val (newFolders, deletedFolders) = MergeUtil.splitSets(this.newFolders ++ diff.newFolders,
      this.deletedFolders ++ diff.deletedFolders, decider.folders)
    copy(path, math.max(time, diff.time), newFiles, deletedFiles, newFolders, deletedFolders)
  }

  def diff(oldDiff: FolderDiff, decider: FolderDecider = FolderDecider.mutualExclude): FolderDiff = {
    require(oldDiff.path == path, "Invalid path")
    merge(oldDiff.reverse, decider)
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

  override def toString: String = {
    if (nonEmpty) {
      s"FolderDiff($path, $time, new files = [${newFiles.mkString(", ")}], deleted files = [${deletedFiles.mkString(", ")}], new folders = [${newFolders.mkString(", ")}], deleted folders = [${deletedFolders.mkString(", ")}])"
    } else {
      s"FolderDiff.empty($path)"
    }
  }
}

object FolderDiff {
  def apply(oldFolder: Folder, newFolder: Folder): FolderDiff = {
    require(oldFolder.path == newFolder.path, "Invalid path")
    val (leftFiles, rightFiles) = MergeUtil.splitSets(oldFolder.files, newFolder.files, SplitDecider.dropDuplicates)
    val (leftFolders, rightFolders) = MergeUtil.splitSets(oldFolder.folders, newFolder.folders, SplitDecider.dropDuplicates)
    FolderDiff(oldFolder.path, newFolder.lastModified, rightFiles, leftFiles, rightFolders, leftFolders)
  }

  def auto(folder1: Folder, folder2: Folder): FolderDiff = {
    val (left, right) = if (folder1.lastModified >= folder2.lastModified) (folder1, folder2) else (folder2, folder1)
    apply(left, right)
  }

  def wrap(folder: Folder): FolderDiff = {
    FolderDiff(folder.path, folder.lastModified, folder.files, Set.empty, folder.folders, Set.empty)
  }
}