package com.karasiq.shadowcloud.model



import akka.util.ByteString
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData}

@SerialVersionUID(0L)
final case class Data(plain: ByteString = ByteString.empty, encrypted: ByteString = ByteString.empty)
  extends SCEntity with HasEmpty with HasWithoutData {

  type Repr = Data

  @transient
  private[this] lazy val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  def isEmpty: Boolean = {
    plain.isEmpty && encrypted.isEmpty
  }

  def withoutData: Data = {
    Data.empty
  }

  override def hashCode(): Int = {
    _hashCode
  }
}

object Data {
  val empty = Data()
}
