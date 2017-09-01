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

  private[this] implicit def castStringToIcon(iconName: String): Icon = iconName.faFwIcon
}
