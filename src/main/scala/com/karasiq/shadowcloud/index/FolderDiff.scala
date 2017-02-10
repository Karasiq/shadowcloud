package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}

import scala.language.postfixOps

case class FolderDiff(path: Path, time: Long = 0, newFiles: Set[File] = Set.empty, deletedFiles: Set[File] = Set.empty, newFolders: Set[String] = Set.empty, deletedFolders: Set[String] = Set.empty) extends HasPath {
  def nonEmpty: Boolean = {
    newFiles.nonEmpty || deletedFiles.nonEmpty || newFolders.nonEmpty || deletedFolders.nonEmpty
  }

  // Delete wins by default
  def merge(diff: FolderDiff, fileDecider: SplitDecider[File] = SplitDecider.dropDuplicates, folderDecider: SplitDecider[String] = SplitDecider.dropDuplicates): FolderDiff = {
    require(diff.path == path, "Invalid path")
    val (newFiles, deletedFiles) = MergeUtil.splitSets(this.newFiles ++ diff.newFiles,
      this.deletedFiles ++ diff.deletedFiles, fileDecider)
    val (newFolders, deletedFolders) = MergeUtil.splitSets(this.newFolders ++ diff.newFolders,
      this.deletedFolders ++ diff.deletedFolders, folderDecider)
    copy(path, math.max(time, diff.time), newFiles, deletedFiles, newFolders, deletedFolders)
  }

  def diff(diff: FolderDiff, fileDecider: Decider[File] = Decider.diff, folderDecider: Decider[String] = Decider.diff): FolderDiff = {
    require(diff.path == path, "Invalid path")
    copy(path, time, MergeUtil.mergeSets(this.newFiles, diff.newFiles, fileDecider),
      MergeUtil.mergeSets(this.deletedFiles, diff.deletedFiles, fileDecider),
      MergeUtil.mergeSets(this.newFolders, diff.newFolders, folderDecider),
      MergeUtil.mergeSets(this.deletedFolders, diff.deletedFolders, folderDecider))
  }

  def reverse: FolderDiff = {
    copy(path, time, deletedFiles, newFiles, deletedFolders, newFolders)
  }
}

object FolderDiff {
  def apply(folder: Folder, secondFolder: Folder): FolderDiff = {
    require(folder.path == secondFolder.path, "Invalid path")

    val (leftFiles, rightFiles) = MergeUtil.splitSets(folder.files, secondFolder.files, SplitDecider.dropDuplicates)
    val (leftFolders, rightFolders) = MergeUtil.splitSets(folder.folders, secondFolder.folders, SplitDecider.dropDuplicates)
    FolderDiff(folder.path, math.max(folder.lastModified, secondFolder.lastModified), rightFiles, leftFiles, rightFolders, leftFolders)
  }

  def wrap(folder: Folder): FolderDiff = {
    FolderDiff(folder.path, folder.lastModified, folder.files, Set.empty, folder.folders, Set.empty)
  }
}