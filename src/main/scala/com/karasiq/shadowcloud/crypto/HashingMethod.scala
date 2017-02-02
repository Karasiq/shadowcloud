package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

sealed trait HashingMethod

object HashingMethod {
  case class Digest(alg: String) extends HashingMethod {
    override def toString = alg 
  }

  def apply(alg: String): HashingMethod = Digest(alg)
  val default = Digest("SHA1")
}