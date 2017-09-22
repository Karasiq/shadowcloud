package com.karasiq.shadowcloud.model.keys

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutKeys}
import com.karasiq.shadowcloud.model.SCEntity

@SerialVersionUID(0L)
final case class KeyChain(encKeys: Seq[KeySet] = Vector.empty,
                          decKeys: Seq[KeySet] = Vector.empty,
                          disabledKeys: Seq[KeySet] = Vector.empty)
  extends SCEntity with HasEmpty with HasWithoutKeys {

  type Repr = KeyChain
  def isEmpty: Boolean = encKeys.isEmpty && decKeys.isEmpty && disabledKeys.isEmpty

  def withoutKeys = {
    copy(encKeys.map(_.withoutKeys), decKeys.map(_.withoutKeys), disabledKeys.map(_.withoutKeys))
  }
}

object KeyChain {
  val empty = new KeyChain()
}
