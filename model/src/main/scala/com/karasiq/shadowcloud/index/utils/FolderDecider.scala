package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.utils.MergeUtil.SplitDecider

final case class FolderDecider(files: SplitDecider[File], folders: SplitDecider[String])

object FolderDecider {
  def createWins: FolderDecider = FolderDecider(SplitDecider.keepLeft, SplitDecider.keepLeft)
  def deleteWins: FolderDecider = FolderDecider(SplitDecider.keepRight, SplitDecider.keepRight)
  def mutualExclude: FolderDecider = FolderDecider(SplitDecider.dropDuplicates, SplitDecider.dropDuplicates)
}
