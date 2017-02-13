package com.karasiq.shadowcloud.utils

import scala.language.postfixOps

trait MessageStatus[Key, Value] {
  sealed trait Status
  case class Success(key: Key, result: Value) extends Status
  case class Failure(key: Key, error: Throwable) extends Status
}
