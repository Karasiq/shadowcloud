package com.karasiq.shadowcloud.config

import akka.util.ByteString
import com.karasiq.shadowcloud.index.utils.HasEmpty

case class SerializedProps(format: String = "", data: ByteString = ByteString.empty) extends HasEmpty {
  def isEmpty: Boolean = {
    data.isEmpty
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