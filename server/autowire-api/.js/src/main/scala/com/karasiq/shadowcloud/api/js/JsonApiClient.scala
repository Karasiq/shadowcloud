package com.karasiq.shadowcloud.api.js

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.ext.Ajax
import play.api.libs.json
import play.api.libs.json._

object JsonApiClient extends autowire.Client[JsValue, json.Reads, json.Writes] {
  def doCall(req: Request): Future[JsValue] = {
    val jsonPayload = Json.stringify(JsObject(req.args))
    val url = ("api" +: req.path).mkString("/", "/", "")
    Ajax.post(url, jsonPayload, headers = Map("Accept" → "application/json", "Content-Type" → "application/json"))
      .map(_.responseText)
      .map(Json.parse)
  }

  def read[Result: Reads](p: JsValue): Result = {
    Json.fromJson[Result](p).get
  }

  def write[Result: Writes](r: Result): JsValue = {
    Json.toJson(r)
  }
}
