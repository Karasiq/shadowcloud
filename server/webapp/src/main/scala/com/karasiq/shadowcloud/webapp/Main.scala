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
import com.karasiq.shadowcloud.webapp.components.folder.{FoldersPanel, UploadForm}
import com.karasiq.shadowcloud.webapp.components.region.{RegionContext, RegionsStoragesPanel, RegionSwitcher}
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
      val regionSwitcher = RegionSwitcher()

      val folderContextRx = regionSwitcher.regionSelector.regionIdRx.fold(None: Option[FolderContext]) { case (oldCtxOpt, newIdOpt) ⇒
        oldCtxOpt.foreach { oldContext ⇒
          oldContext.scope.kill()
          oldContext.selected.kill()
          oldContext.updates.kill()
        }
        newIdOpt.map(FolderContext(_))
      }

      val foldersPanelRx = folderContextRx.map[Frag] {
        case Some(folderContext) ⇒
          folderContext.selected() = testFolder
          regionSwitcher.scopeSelector.selectedScope.foreach(folderContext.scope.update)
          FoldersPanel()(appContext, folderContext)

        case None ⇒
          Bootstrap.noContent
      }

      val uploadFormRx = folderContextRx.map[Frag] {
        case Some(folderContext) ⇒
          UploadForm()(appContext, folderContext).renderButton()

        case None ⇒
          Bootstrap.noContent
      }


      val foldersPanel = div(
        GridSystem.row(
          GridSystem.col(6).asDiv(regionSwitcher),
          GridSystem.col(6).asDiv(uploadFormRx)
        ),
        GridSystem.mkRow(foldersPanelRx)
      )

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
