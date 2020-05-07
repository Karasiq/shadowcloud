package com.karasiq.shadowcloud.webapp.components.common

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@scalajs.js.native
@JSGlobal("toastr")
object Toastr extends js.Object {
  def info(text: String): Unit                   = js.native
  def info(text: String, title: String): Unit    = js.native
  def success(text: String): Unit                = js.native
  def success(text: String, title: String): Unit = js.native
  def warning(text: String): Unit                = js.native
  def warning(text: String, title: String): Unit = js.native
  def error(text: String): Unit                  = js.native
  def error(text: String, title: String): Unit   = js.native
  val options: ToastrOptions                     = js.native
}

@js.native
trait ToastrOptions extends js.Object {
  var timeOut: Int
  var extendedTimeOut: Int

  var escapeHtml: Boolean
  var newestOnTop: Boolean
  var preventDuplicates: Boolean
  var progressBar: Boolean
  var rtl: Boolean

  var closeButton: Boolean
  var closeHtml: String
  var closeDuration: Int

  var showMethod: String
  var hideMethod: String
  var closeMethod: String

  var showEasing: String
  var closeEasing: String
  var hideEasing: String

  var onShown: js.Function0[Unit]
  var onHidden: js.Function0[Unit]
  var onclick: js.Function0[Unit]
  var onCloseClick: js.Function0[Unit]
}
