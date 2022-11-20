package com.karasiq.shadowcloud.index.utils

import com.karasiq.shadowcloud.model.File

final case class FolderDiffDecider(files: DiffMergeDecider[File], folders: DiffMergeDecider[String])

object FolderDiffDecider {
  val idempotent = FolderDiffDecider(DiffMergeDecider.idempotent, DiffMergeDecider.idempotent)
  val leftWins   = FolderDiffDecider(DiffMergeDecider.leftWins, DiffMergeDecider.leftWins)
  val rightWins  = FolderDiffDecider(DiffMergeDecider.rightWins, DiffMergeDecider.rightWins)
}
