package com.karasiq.shadowcloud.webapp.components.common
import scala.language.implicitConversions

import com.karasiq.bootstrap.Bootstrap.default._

object AppIcons {
  type Icon = IconModifier

  val close: Icon = "close"
  val preview: Icon = "file-image-o"
  val metadata: Icon = "file-code-o"

  private[this] implicit def castStringToIcon(iconName: String): Icon = iconName.faFwIcon
}
