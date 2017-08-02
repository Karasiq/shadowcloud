package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import play.api.libs.json
import play.api.libs.json._

import com.karasiq.shadowcloud.api.SCApiClient

trait SCJsonApiClient extends SCApiClient[JsValue, json.Reads, json.Writes] with SCJsonApi {
  def read[Result: Reads](p: JsValue): Result = {
    Json.fromJson[Result](p).get
  }

  def write[Result: Writes](r: Result): JsValue = {
    Json.toJson(r)
  }

  def encodePayload(value: Map[String, JsValue]): ByteString = {
    ByteString(Json.toBytes(JsObject(value)))
  }
}
