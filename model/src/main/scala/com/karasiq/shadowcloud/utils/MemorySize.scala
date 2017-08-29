package com.karasiq.shadowcloud.utils

import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

import scala.language.postfixOps

case class MemorySize(bytes: Long) extends AnyVal {
  override def toString: String = {
    MemorySize.toString(bytes)
  }
}

object MemorySize {
  private[this] val decimalFormat = new DecimalFormat("#0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
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
    val sb = new StringBuilder(8)
    if (bytes < 1024) {
      sb.append(bytes)
      sb.append(" bytes")
    } else {
      val (amount, unit) = toCoarsest(bytes)
      sb.append(decimalFormat.format(amount))
      sb.append(' ')
      sb.append(unit)
    }
    sb.result()
  }
}
