package com.karasiq.shadowcloud.api.boopickle

import com.karasiq.shadowcloud.api.SCApiMeta

trait SCBooPickleApi extends SCApiMeta {
  type EncodingT = SCBooPickleEncoding
  object encoding extends SCBooPickleEncoding
  val payloadContentType = "binary/boopickle"
}
