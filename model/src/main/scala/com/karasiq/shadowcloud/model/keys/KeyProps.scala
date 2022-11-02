package com.karasiq.shadowcloud.model.keys

import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.model.keys.KeyProps.RegionSet

@SerialVersionUID(0L)
final case class KeyProps(key: KeySet, regionSet: RegionSet, forEncryption: Boolean, forDecryption: Boolean)

object KeyProps {
  type RegionSet = Set[RegionId]
  object RegionSet {
    val all: RegionSet = Set.empty

    def enabledOn(regionSet: RegionSet, regionId: RegionId) = regionSet.isEmpty || regionSet.contains(regionId)
  }
}
