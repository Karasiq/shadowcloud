package com.karasiq.shadowcloud.api.js

import scala.concurrent.Future
import scala.language.higherKinds

import akka.util.ByteString
import boopickle.Pickler
import org.scalajs.dom.ext.Ajax
import play.api.libs.json._

import com.karasiq.shadowcloud.api.{SCApiClient, SCApiUtils}
import com.karasiq.shadowcloud.api.boopickle.SCBooPickleApiClient
import com.karasiq.shadowcloud.api.json.SCJsonApiClient

trait SCAjaxApiClient[Reader[_], Writer[_]] extends SCApiClient[ByteString, Reader, Writer] {
  import utils.AjaxUtils._

  def doCall(req: Request): Future[ByteString] = {
    val payload = encodePayload(req.args)
    val url     = ("api" +: req.path).mkString("/", "/", "")
    val headers = Map(
      "SC-Accept"        → payloadContentType,
      "Content-Type"     → payloadContentType,
      "X-Requested-With" → SCApiUtils.RequestedWith
    )

    Ajax.post(url, payload.toByteBuffer, /* 15000 */ 0, headers, responseType = "arraybuffer").reportFailure.responseBytes
  }
}

object SCAjaxJsonApiClient      extends SCAjaxApiClient[Reads, Writes] with SCJsonApiClient
object SCAjaxBooPickleApiClient extends SCAjaxApiClient[Pickler, Pickler] with SCBooPickleApiClient
