package com.karasiq.shadowcloud.index.utils

import scala.language.postfixOps

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.utils.MergeUtil.SplitDecider

final case class FolderDecider(files: SplitDecider[File], folders: SplitDecider[String])

object FolderDecider {
  val createWins = FolderDecider(SplitDecider.keepLeft, SplitDecider.keepLeft)
  val deleteWins = FolderDecider(SplitDecider.keepRight, SplitDecider.keepRight)
  val mutualExclude = FolderDecider(SplitDecider.dropDuplicates, SplitDecider.dropDuplicates)
}
