package com.karasiq.shadowcloud.model



import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class Timestamp(created: Long, lastModified: Long) extends SCEntity with Comparable[Timestamp] {
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
  val empty = Timestamp(0L, 0L)

  def now: Timestamp = {
    val timestamp = Utils.timestamp
    Timestamp(timestamp, timestamp)
  }

  implicit val ordering: Ordering[Timestamp] =
    Ordering.by(ts ⇒ (ts.lastModified, ts.created))
}
