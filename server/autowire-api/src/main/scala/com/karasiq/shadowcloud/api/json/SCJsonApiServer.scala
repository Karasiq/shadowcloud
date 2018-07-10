package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import play.api.libs.json._

import com.karasiq.shadowcloud.api.SCApiServer

trait SCJsonApiServer extends SCApiServer[ByteString, Reads, Writes] with SCJsonApi {
  def read[Result: Reads](p: ByteString): Result = {
    Json.fromJson(Json.parse(p.toArray)).get
  }

  def write[Result: Writes](r: Result): ByteString = {
    ByteString.fromArrayUnsafe(Json.toBytes(Json.toJson(r)))
  }

  def decodePayload(payload: ByteString): Map[String, ByteString] = {
    Json.parse(payload.toArray).as[JsObject].fields.toMap.mapValues(v ⇒ ByteString(v.as[JsString].value))
  }
}
