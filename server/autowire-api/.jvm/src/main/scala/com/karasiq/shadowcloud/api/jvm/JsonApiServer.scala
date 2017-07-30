package com.karasiq.shadowcloud.api.jvm

import play.api.libs.json.{Json, JsValue, Reads, Writes}

object JsonApiServer extends autowire.Server[JsValue, Reads, Writes] {
  def read[Result](p: JsValue)(implicit evidence$1: Reads[Result]): Result = {
    Json.fromJson(p).get
  }

  def write[Result](r: Result)(implicit evidence$2: Writes[Result]): JsValue = {
    Json.toJson(r)
  }
}
