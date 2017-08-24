package com.karasiq.shadowcloud.api.js

import scala.concurrent.Future

import org.scalajs.dom.ext.Ajax
import play.api.libs.json._

import com.karasiq.shadowcloud.api.{SCApiClient, SCApiUtils}
import com.karasiq.shadowcloud.api.json.SCJsonApiClient

trait SCAjaxApiClient extends SCApiClient[JsValue, Reads, Writes] {
  import utils.AjaxUtils._

  def doCall(req: Request): Future[JsValue] = {
    val jsonPayload = encodePayload(req.args)
    val url = ("api" +: req.path).mkString("/", "/", "")
    val headers = Map(
      "SC-Accept" → payloadContentType,
      "Content-Type" → payloadContentType,
      "X-Requested-With" → SCApiUtils.requestedWith
    )
    
    Ajax.post(url, jsonPayload.toByteBuffer, 15000, headers, responseType = "arraybuffer")
      .reportFailure
      .responseBytes
      .map(bs ⇒ Json.parse(bs.toArray))
  }
}

object SCAjaxApiClient extends SCAjaxApiClient with SCJsonApiClient
