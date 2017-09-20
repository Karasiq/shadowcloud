package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.folder.FoldersPanel
import com.karasiq.shadowcloud.webapp.components.region.{RegionContext, RegionsStoragesPanel}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      // Context
      implicit val appContext = AppContext()

      // Styles
      AppContext.applyStyles()

      // Content
      val testRegion = "testRegion"
      val testFolder = Path.root / "TestFolder" / "TestSubFolder" / "TestSubSubFolder" / "/* TestSubSubSubFolder */"
      appContext.api.createFolder(testRegion, testFolder).foreach(println)

      implicit val regionContext = RegionContext()
      implicit val folderContext = FolderContext(testRegion)
      folderContext.selected() = testFolder

      val foldersPanel = FoldersPanel(testRegion)
      val regionsPanel = RegionsStoragesPanel()

      val navigationBar = NavigationBar()
        .withBrand("shadowcloud")
        .withContentContainer(GridSystem.containerFluid(_))
        .withStyles(NavigationBarStyle.default, NavigationBarStyle.staticTop)
        .withTabs(
          NavigationTab(appContext.locale.foldersView, "folders", AppIcons.foldersView, foldersPanel),
          NavigationTab(appContext.locale.regionsView, "regions", AppIcons.regionsView, regionsPanel)
        )

      navigationBar.applyTo(dom.document.body)
    })
  }
}
