package com.karasiq.shadowcloud.model.keys

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutKeys}
import com.karasiq.shadowcloud.model.{RegionId, SCEntity}
import com.karasiq.shadowcloud.model.keys.KeyProps.RegionSet

@SerialVersionUID(1L)
final case class KeyChain(keys: Seq[KeyProps]) extends SCEntity with HasEmpty with HasWithoutKeys {

  type Repr = KeyChain
  def isEmpty: Boolean = keys.isEmpty

  def withoutKeys = {
    copy(keys.map(kp ⇒ kp.copy(kp.key.withoutKeys)))
  }

  def forRegion(regionId: RegionId): KeyChain = {
    copy(keys.filter(kp ⇒ RegionSet.enabledOn(kp.regionSet, regionId)))
  }

  def encKeys = keys.filter(_.forEncryption).map(_.key)
  def decKeys = keys.filter(_.forDecryption).map(_.key)
}

object KeyChain {
  val empty = new KeyChain(Nil)
}
