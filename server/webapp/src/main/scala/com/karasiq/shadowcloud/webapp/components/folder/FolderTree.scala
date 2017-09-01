package com.karasiq.shadowcloud.webapp.components.folder

import scala.language.postfixOps

import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{Path, RegionId}
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils

object FolderTree {
  def apply(regionId: RegionId, path: Path)(implicit context: AppContext, folderContext: FolderContext): FolderTree = {
    new FolderTree(regionId, path)
  }

  private def isOpened(path: Path)(implicit fc: FolderContext): Boolean = {
    fc.selected.now.startsWith(path)
  }
}

class FolderTree(regionId: RegionId, path: Path)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  val opened = Var(FolderTree.isOpened(path))

  private[this] val link = renderLink(opened, path)
  private[this] val content = renderContent(opened, regionId, path)

  def renderTag(md: ModifierT*): TagT = {
    div(
      link,
      content,
      md
    )
  }

  private[this] def renderLink(opened: Var[Boolean], path: Path): ModifierT = {
    val pathString = if (path.isRoot) context.locale.rootPath else path.name
    Rx {
      val icon = if (opened()) AppIcons.folderOpen else AppIcons.folder
      val styles = if (folderContext.selected() == path) {
        Seq(Bootstrap.textStyle.success, fontWeight.bold)
      } else {
        Seq(Bootstrap.textStyle.primary)
      }

      span(
        a(href := "#", icon, styles, onclick := Callback.onClick(_ ⇒ opened() = !opened.now)),
        Bootstrap.nbsp,
        a(href := "#", pathString, styles, onclick := Callback.onClick(_ ⇒ folderContext.selected() = path))
      )
    }
  }

  private[this] def renderContent(opened: Var[Boolean], regionId: RegionId, path: Path): ModifierT = {
    lazy val subFoldersRx = {
      val folderRx = RxUtils.toFolderRx(regionId, Var(path))
      Rx {
        val rootFolder = folderRx.toRx()
        val subFolders = rootFolder.folders.toVector.sorted
        for (subFolder ← subFolders)
          yield FolderTree(regionId, rootFolder.path / subFolder)
      }
    }

    Rx[Frag] {
      if (opened()) {
        div(Rx(div(
          paddingLeft := 5.px,
          subFoldersRx.apply().map(_.renderTag())
        )))
      } else {
        ()
      }
    }
  }
}


