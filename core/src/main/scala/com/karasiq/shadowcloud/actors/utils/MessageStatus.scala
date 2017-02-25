package com.karasiq.shadowcloud.actors.utils

import akka.actor.DeadLetterSuppression

import scala.language.postfixOps

trait MessageStatus[Key, Value] {
  sealed trait Status extends DeadLetterSuppression {
    def key: Key
  }
  case class Success(key: Key, result: Value) extends Status
  case class Failure(key: Key, error: Throwable) extends Status
}
