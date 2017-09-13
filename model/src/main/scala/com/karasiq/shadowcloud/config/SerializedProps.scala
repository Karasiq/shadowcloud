package com.karasiq.shadowcloud.config

import scala.util.hashing.MurmurHash3

import akka.util.ByteString

import com.karasiq.shadowcloud.index.utils.HasEmpty

@SerialVersionUID(0L)
case class SerializedProps(format: String = "", data: ByteString = ByteString.empty) extends HasEmpty {
  @transient
  private[this] lazy val _hashCode = MurmurHash3.productHash(this)

  def isEmpty: Boolean = {
    data.isEmpty
  }

  override def hashCode(): Int = {
    _hashCode
  }

  override def toString: String = {
    if (isEmpty) {
      "SerializedProps.empty"
    } else {
      s"SerializedProps($format, ${data.length} bytes)"
    }
  }
}

object SerializedProps {
  val empty = SerializedProps()
}