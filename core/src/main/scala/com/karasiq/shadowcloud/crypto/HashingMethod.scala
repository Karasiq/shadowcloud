package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

sealed trait HashingMethod

object HashingMethod {
  case object NoHashing extends HashingMethod

  case class Digest(algorithm: String) extends HashingMethod {
    override def toString: String = algorithm
  }

  def apply(alg: String): HashingMethod = {
    Digest(alg)
  }

  val none = NoHashing
  val default = Digest("SHA1")
}