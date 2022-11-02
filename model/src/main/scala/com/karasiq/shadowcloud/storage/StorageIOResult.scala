package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path

sealed trait StorageIOResult {
  def path: Path
  def count: Long
  def isSuccess: Boolean
  final def isFailure: Boolean = !isSuccess
}

object StorageIOResult {
  final case class Success(path: Path, count: Long) extends StorageIOResult {
    val isSuccess: Boolean = true
  }

  final case class Failure(path: Path, error: StorageException) extends StorageIOResult {
    val count: Long        = 0L
    val isSuccess: Boolean = false
  }

  val empty: StorageIOResult = Success(Path.root, 0L)
}
