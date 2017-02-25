package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

sealed trait EncryptionMethod

object EncryptionMethod {
  case object Plain extends EncryptionMethod
  case class AES(mode: String = "GCM", bits: Int = 256) extends EncryptionMethod

  val default = AES()
}