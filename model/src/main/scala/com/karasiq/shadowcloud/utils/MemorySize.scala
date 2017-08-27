package com.karasiq.shadowcloud.utils

import scala.language.postfixOps

case class MemorySize(bytes: Long) extends AnyVal {
  override def toString: String = {
    MemorySize.toString(bytes)
  }
}

object MemorySize {
  private[this] val units = Array("bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")

  val KB: Int = 1024
  val MB: Int = KB * 1024
  val GB: Long = MB * 1024
  val TB: Long = GB * 1024
  val PB: Long = TB * 1024
  val EB: BigInt = PB * 1024L
  val ZB: BigInt = EB * 1024L
  val YB: BigInt = ZB * 1024L

  def toBytes(unit: String): Long = {
    val index = units.indexWhere(_.equalsIgnoreCase(unit))
    Math.pow(1024, math.max(0, index)).toLong
  }

  def toBytes(amount: Double, unit: String): Long = {
    (amount * toBytes(unit)).toLong
  }

  def toCoarsest(bytes: Long): (Double, String) = {
    val unit: Int = math.max(0, math.min(units.length - 1, (Math.log10(bytes) / Math.log10(1024)).toInt))
    (bytes / Math.pow(1024, unit), units(unit))
  }

  def toString(bytes: Long): String = {
    if (bytes < 1024) {
      s"$bytes bytes"
    } else {
      val (amount, unit) = toCoarsest(bytes)
      f"$amount%.2f $unit"
    }
  }
}
