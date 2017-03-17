package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.exceptions.StorageException

import scala.language.postfixOps

sealed trait StorageIOResult {
  def path: String
  def count: Long
  def isSuccess: Boolean
  final def isFailure: Boolean = !isSuccess
}

object StorageIOResult {
  final case class Success(path: String, count: Long) extends StorageIOResult {
    val isSuccess: Boolean = true
  }

  final case class Failure(path: String, error: StorageException) extends StorageIOResult {
    val count: Long = 0L
    val isSuccess: Boolean = false
  }
}
