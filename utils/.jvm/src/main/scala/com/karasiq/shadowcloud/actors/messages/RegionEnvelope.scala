package com.karasiq.shadowcloud.actors.messages

import com.karasiq.shadowcloud.model.RegionId

case class RegionEnvelope(regionId: RegionId, message: Any)
