package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import play.api.libs.json
import play.api.libs.json._

import com.karasiq.shadowcloud.api.SCApiClient

trait SCJsonApiClient extends SCApiClient[ByteString, json.Reads, json.Writes] with SCJsonApi {
  def read[Result: Reads](p: ByteString): Result = {
    Json.fromJson[Result](Json.parse(p.toArray)).get
  }

  def write[Result: Writes](r: Result): ByteString = {
    ByteString(Json.toBytes(Json.toJson(r)))
  }

  def encodePayload(value: Map[String, ByteString]): ByteString = {
    ByteString(Json.toBytes(JsObject(value.mapValues(bs ⇒ JsString(bs.utf8String)))))
  }
}
