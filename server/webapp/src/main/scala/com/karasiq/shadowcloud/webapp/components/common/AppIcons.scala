package com.karasiq.shadowcloud.webapp.components.common
import scala.language.implicitConversions

import com.karasiq.bootstrap.Bootstrap.default._

object AppIcons {
  type Icon = FontAwesomeIcon

  val close: Icon = "close"

  val foldersView: Icon = "folder"
  val regionsView: Icon = "server"

  val preview: Icon = "file-image-o"
  val metadata: Icon = "file-code-o"

  val file: Icon = "file-o"
  val folder: Icon = "folder-o"
  val folderOpen: Icon = "folder-open-o"

  val refresh: Icon = "refresh"
  val create: Icon = "plus"
  val delete: Icon = "trash"
  val rename: Icon = "pencil-square-o"
  val compress: Icon = "compress"

  val play: Icon = "film"
  val download: Icon = "download"
  val upload: Icon = "upload"
  val viewText: Icon = "file-text-o"
  val editText: Icon = "edit"
  val inspect: Icon = "user-secret"
  val repair: Icon = "wrench"
  val changeView: Icon = "list-alt"

  val revisions: Icon = "clone"
  val availability: Icon = "balance-scale"
  val partiallyAvailable: Icon = "exclamation-triangle"
  val fullyAvailable: Icon = "check"

  val region: Icon = "microchip"
  val currentScope: Icon = "check"
  val historyScope: Icon = "clock-o"

  val register: Icon = "plug"
  val suspend: Icon = "pause"
  val resume: Icon = "play"

  val logs: Icon = "book"

  private[this] implicit def castStringToIcon(iconName: String): Icon = iconName.faFwIcon
}
