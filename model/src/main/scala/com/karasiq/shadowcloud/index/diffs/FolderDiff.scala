package com.karasiq.shadowcloud.index.diffs

import scala.language.postfixOps

import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.utils._
import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.SplitDecider

case class FolderDiff(path: Path, time: Long = 0, newFiles: Set[File] = Set.empty,
                      deletedFiles: Set[File] = Set.empty, newFolders: Set[String] = Set.empty,
                      deletedFolders: Set[String] = Set.empty)
  extends HasPath with MergeableDiff with HasEmpty with HasWithoutData {

  type Repr = FolderDiff

  def mergeWith(diff: FolderDiff, decider: FolderDecider = FolderDecider.mutualExclude): FolderDiff = {
    require(diff.path == path, "Invalid path")
    val (newFiles, deletedFiles) = MergeUtil.splitSets(this.newFiles ++ diff.newFiles,
      this.deletedFiles ++ diff.deletedFiles, decider.files)
    val (newFolders, deletedFolders) = MergeUtil.splitSets(this.newFolders ++ diff.newFolders,
      this.deletedFolders ++ diff.deletedFolders, decider.folders)
    copy(path, math.max(time, diff.time), newFiles, deletedFiles, newFolders, deletedFolders)
  }

  def diffWith(oldDiff: FolderDiff, decider: FolderDecider = FolderDecider.mutualExclude): FolderDiff = {
    require(oldDiff.path == path, "Invalid path")
    mergeWith(oldDiff.reverse, decider)
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