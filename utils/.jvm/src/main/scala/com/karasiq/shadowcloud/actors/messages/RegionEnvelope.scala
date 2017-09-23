package com.karasiq.shadowcloud.actors.messages

import com.karasiq.shadowcloud.model.RegionId

@SerialVersionUID(0L)
final case class RegionEnvelope(regionId: RegionId, message: Any)
