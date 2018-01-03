package com.karasiq.shadowcloud.webapp.styles

import scala.language.postfixOps

import com.karasiq.shadowcloud.webapp.context.AppContext.CssSettings._

object FolderTreeStyles extends StyleSheet.Inline {
  import dsl._

  val subTree = style(
    paddingLeft(5 px)
  )

  val folderActions = style(
    marginLeft(2 px),
    cursor.pointer,
    opacity(0.3),
    &.hover(
      opacity(0.8)
    )
  )
}
