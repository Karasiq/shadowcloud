package com.karasiq.shadowcloud.api.js.utils

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}

import akka.util.ByteString
import org.scalajs.dom.XMLHttpRequest

object AjaxUtils {
  implicit val executionContext: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  implicit class AjaxResultOps(future: Future[XMLHttpRequest]) {
    def responseBytes: Future[ByteString] = {
      future
        .filter(_.status == 200)
        .map(r ⇒ ByteString(TypedArrayBuffer.wrap(r.response.asInstanceOf[ArrayBuffer])))
    }

    def reportFailure: Future[XMLHttpRequest] = {
      future.onComplete(_.failed.foreach(System.err.println))
      future
    }
  }
}
