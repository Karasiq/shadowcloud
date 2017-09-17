package com.karasiq.shadowcloud.webapp.components.common
import scala.language.implicitConversions

import com.karasiq.bootstrap.Bootstrap.default._

object AppIcons {
  type Icon = FontAwesomeIcon

  val close: Icon = "close"
  val preview: Icon = "file-image-o"
  val metadata: Icon = "file-code-o"

  val folder: Icon = "folder-o"
  val folderOpen: Icon = "folder-open-o"

  val refresh: Icon = "refresh"
  val create: Icon = "plus"
  val delete: Icon = "trash"
  val play: Icon = "play-circle-o"
  val download: Icon = "download"
  val viewText: Icon = "file-text-o"
  val editText: Icon = "edit"

  val revisions: Icon = "clone"
  val availability: Icon = "balance-scale"
  val partiallyAvailable: Icon = "exclamation-triangle"
  val fullyAvailable: Icon = "check"

  val currentScope: Icon = "check"
  val historyScope: Icon = "clock-o"

  private[this] implicit def castStringToIcon(iconName: String): Icon = iconName.faFwIcon
}
