package com.karasiq.shadowcloud.model.keys

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.SCEntity

@SerialVersionUID(0L)
final case class KeyChain(encKeys: Seq[KeySet], decKeys: Seq[KeySet]) extends SCEntity with HasEmpty {
  def isEmpty: Boolean = encKeys.isEmpty && decKeys.isEmpty
}

object KeyChain {
  val empty = KeyChain(Nil, Nil)
}
