package com.karasiq.shadowcloud.webapp.utils

import scala.concurrent.{Future, Promise}

import akka.util.ByteString
import org.scalajs.dom
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.ext.Ajax.InputData
import rx.{Rx, Var}

import com.karasiq.shadowcloud.api.js.utils.AjaxUtils._
import com.karasiq.shadowcloud.utils.Utils

object UploadUtils {
  type XHR = dom.XMLHttpRequest

  def uploadWithProgress(
      url: String,
      data: InputData,
      method: String = "POST",
      timeout: Int = 0,
      headers: Map[String, String] = Map.empty,
      withCredentials: Boolean = false,
      responseType: String = "arraybuffer"
  ): (Rx[Int], Future[ByteString]) = {

    val (request, completeFuture) = doRequest(method, url, data, timeout, headers, withCredentials, responseType)
    val progressRx                = Var(0)

    request.upload.addEventListener(
      "progress",
      { (e: dom.ProgressEvent) ⇒
        // println(e.loaded + " " + e.total)
        val progress = Utils.percents(e.loaded, e.total)
        progressRx() = progress.toInt
      },
      false
    )

    // completeFuture.onComplete(_ ⇒ progressRx.kill())
    (progressRx, completeFuture.reportFailure.responseBytes)
  }

  private[this] def doRequest(
      method: String,
      url: String,
      data: InputData,
      timeout: Int,
      headers: Map[String, String],
      withCredentials: Boolean,
      responseType: String
  ): (XHR, Future[XHR]) = {
    val xhr     = new XHR()
    val promise = Promise[XHR]()

    xhr.onreadystatechange = { (_: dom.Event) ⇒
      if (xhr.readyState == 4) {
        if ((xhr.status >= 200 && xhr.status < 300) || xhr.status == 304)
          promise.success(xhr)
        else
          promise.tryFailure(AjaxException(xhr))
      }
    }

    xhr.onerror = { (_: dom.Event) ⇒
      promise.tryFailure(AjaxException(xhr))
    }

    xhr.open(method, url)
    xhr.responseType = responseType
    xhr.timeout = timeout
    xhr.withCredentials = withCredentials
    headers.foreach(kv ⇒ xhr.setRequestHeader(kv._1, kv._2))

    if (data == null) xhr.send() else xhr.send(data)

    (xhr, promise.future)
  }
}
