package com.karasiq.shadowcloud.webapp.utils

import scala.concurrent.{Future, Promise}

import akka.util.ByteString
import org.scalajs.dom
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.ext.Ajax.InputData
import rx.{Rx, Var}

import com.karasiq.shadowcloud.api.js.utils.AjaxUtils._
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object UploadUtils {
  type XHR = dom.XMLHttpRequest

  def uploadWithProgress(url: String, data: InputData, method: String = "POST", timeout: Int = 0,
                         headers: Map[String, String] = Map.empty, withCredentials: Boolean = false,
                         responseType: String = "arraybuffer"): (Rx[Int], Future[ByteString]) = {

    val (request, completeFuture) = doRequest(method, url, data, timeout, headers, withCredentials, responseType)
    val progressRx = Var(0)

    request.upload.addEventListener("progress", { (e: dom.ProgressEvent) ⇒
      // println(e.loaded + " " + e.total)
      val progress = Utils.percents(e.loaded, e.total)
      progressRx() = progress.toInt
    }, false)

    completeFuture.onComplete(_ ⇒ progressRx.kill())
    (progressRx, completeFuture.reportFailure.responseBytes)
  }

  private[this] def doRequest(method: String, url: String, data: InputData, timeout: Int,
                              headers: Map[String, String], withCredentials: Boolean,
                              responseType: String): (XHR, Future[XHR]) = {
    val req = new XHR()
    val promise = Promise[XHR]()

    req.onreadystatechange = { (_: dom.Event) =>
      if (req.readyState == 4) {
        if ((req.status >= 200 && req.status < 300) || req.status == 304)
          promise.success(req)
        else
          promise.failure(AjaxException(req))
      }
    }
    req.open(method, url)
    req.responseType = responseType
    req.timeout = timeout
    req.withCredentials = withCredentials
    headers.foreach(x ⇒ req.setRequestHeader(x._1, x._2))
    if (data == null)
      req.send()
    else
      req.send(data)
    (req, promise.future)
  }
}
