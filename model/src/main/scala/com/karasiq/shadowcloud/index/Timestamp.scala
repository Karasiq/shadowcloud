package com.karasiq.shadowcloud.index

import scala.language.postfixOps

import com.karasiq.shadowcloud.utils.Utils

case class Timestamp(created: Long, lastModified: Long) extends Comparable[Timestamp] {
  require(lastModified >= created, "Invalid timestamp")

  def modified(at: Long): Timestamp = {
    copy(lastModified = math.max(lastModified, at))
  }

  def modifiedNow: Timestamp = {
    modified(Utils.timestamp)
  }

  def compareTo(o: Timestamp): Int = {
    (lastModified - o.lastModified).toInt
  }
}

object Timestamp {
  def now: Timestamp = {
    val ts = Utils.timestamp
    Timestamp(ts, ts)
  }

  implicit val ordering: Ordering[Timestamp] =
    Ordering.by(ts ⇒ (ts.lastModified, ts.created))
}