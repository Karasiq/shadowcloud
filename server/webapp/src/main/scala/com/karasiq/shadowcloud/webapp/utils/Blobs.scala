package com.karasiq.shadowcloud.webapp.utils

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.typedarray.Uint8Array
import scalatags.JsDom.all._

import org.scalajs.dom
import org.scalajs.dom.{Blob, Event}
import org.scalajs.dom.raw._

/**
  * Blob/file utility
  */
object Blobs {
  def fromBytes(data: Array[Byte], contentType: String = ""): Blob = {
    import scala.scalajs.js.JSConverters._
    val array = new Uint8Array(data.toJSArray)
    new Blob(js.Array(array), BlobPropertyBag(contentType))
  }

  def fromChars(data: Array[Char], contentType: String = ""): Blob = {
    fromBytes(data.map(_.toByte), contentType)
  }

  def fromString(data: String, contentType: String = ""): Blob = {
    fromChars(data.toCharArray, contentType)
  }

  def fromBase64(base64: String, contentType: String = ""): Blob = {
    fromString(dom.window.atob(base64), contentType)
  }

  def saveBlob(blob: Blob, fileName: String): Unit = {
    val url = URL.createObjectURL(blob)
    val anchor = a(href := url, attr("download") := fileName, target := "_blank", display.none).render
    dom.document.body.appendChild(anchor)
    dom.window.setTimeout(() ⇒ {
      dom.document.body.removeChild(anchor)
      URL.revokeObjectURL(url)
    }, 500)
    anchor.click()
  }

  def getUrl(blob: Blob): String = {
    URL.createObjectURL(blob)
  }

  def toDataURL(blob: Blob): Future[String] = {
    val promise = Promise[String]
    val reader = new FileReader
    reader.readAsDataURL(blob)
    reader.onloadend = (_: ProgressEvent) ⇒ {
      promise.success(reader.result.asInstanceOf[String])
    }

    reader.onerror = (errorEvent: Event) ⇒ {
      promise.failure(new IllegalArgumentException(errorEvent.toString))
    }

    promise.future
  }

  def toBase64(blob: Blob)(implicit ec: ExecutionContext): Future[String] = {
    toDataURL(blob).map(_.split(",", 2).last)
  }

  def toString(blob: Blob): Future[String] = {
    val promise = Promise[String]
    val reader = new FileReader
    reader.readAsText(blob)
    reader.onloadend = (_: ProgressEvent) ⇒ {
      promise.success(reader.result.asInstanceOf[String])
    }

    reader.onerror = (errorEvent: Event) ⇒ {
      promise.failure(new IllegalArgumentException(errorEvent.toString))
    }

    promise.future
  }
}
