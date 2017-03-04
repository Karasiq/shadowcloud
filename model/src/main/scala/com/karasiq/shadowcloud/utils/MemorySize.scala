package com.karasiq.shadowcloud.utils

import scala.language.postfixOps

private[shadowcloud] object MemorySize {
  private[this] val UNITS = Array("bytes", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
  val KB = 1024
  val MB = KB * 1024
  val GB = MB * 1024L
  val TB = GB * 1024L
  val PB = TB * 1024L
  val EB = PB * 1024L
  val ZB = EB * 1024L
  val YB = ZB * 1024L

  def toBytes(unit: String): Long = {
    val index = UNITS.indexWhere(_.equalsIgnoreCase(unit))
    Math.pow(1024, math.max(0, index)).toLong
  }

  def toBytes(amount: Double, unit: String): Long = {
    (amount * toBytes(unit)).toLong
  }

  def toCoarsest(bytes: Long): (Double, String) = {
    val unit: Int = math.max(0, math.min(UNITS.length - 1, (Math.log10(bytes) / Math.log10(1024)).toInt))
    (bytes / Math.pow(1024, unit), UNITS(unit))
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
