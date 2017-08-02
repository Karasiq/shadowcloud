package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import play.api.libs.json._

import com.karasiq.shadowcloud.api.SCApiServer

trait SCJsonApiServer extends SCApiServer[JsValue, Reads, Writes] with SCJsonApi {
  def read[Result](p: JsValue)(implicit evidence$1: Reads[Result]): Result = {
    Json.fromJson(p).get
  }

  def write[Result](r: Result)(implicit evidence$2: Writes[Result]): JsValue = {
    Json.toJson(r)
  }

  def decodePayload(payload: ByteString): Map[String, JsValue] = {
    Json.parse(payload.toArray).as[JsObject].fields.toMap
  }
}
