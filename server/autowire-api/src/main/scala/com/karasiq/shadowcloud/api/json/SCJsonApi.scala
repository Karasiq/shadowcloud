package com.karasiq.shadowcloud.api.json

import com.karasiq.shadowcloud.api.SCApiMeta

trait SCJsonApi extends SCApiMeta {
  type EncodingT = SCJsonEncoding
  object encoding extends SCJsonEncoding

  val payloadContentType = "application/json"
}
