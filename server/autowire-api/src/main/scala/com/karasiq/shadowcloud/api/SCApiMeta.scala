package com.karasiq.shadowcloud.api

trait SCApiMeta {
  type EncodingT <: SCApiEncoding
  val encoding: EncodingT
  val payloadContentType: String
}
