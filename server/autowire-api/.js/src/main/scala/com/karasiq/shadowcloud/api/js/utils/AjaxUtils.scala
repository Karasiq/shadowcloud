package com.karasiq.shadowcloud.api.js.utils

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

import akka.util.ByteString
import org.scalajs.dom.XMLHttpRequest

object AjaxUtils {
  implicit val AjaxExecutionContext: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  val BytesResponseType = "arraybuffer"

  implicit class AjaxResultOps(private val future: Future[XMLHttpRequest]) extends AnyVal {
    def responseBytes: Future[ByteString] = {
      future
        // .filter(_.status == 200)
        .map(r ⇒ ByteString(TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer])))
    }

    def reportFailure: Future[XMLHttpRequest] = {
      future.onComplete(_.failed.foreach(System.err.println(_)))
      future
    }
  }
}
