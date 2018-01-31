package com.karasiq.shadowcloud.webapp.components

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.folder.{FoldersPanel, UploadForm}
import com.karasiq.shadowcloud.webapp.components.keys.KeysContext
import com.karasiq.shadowcloud.webapp.components.region.{RegionContext, RegionsStoragesPanel, RegionSwitcher}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.{FileController, FolderController}

object SCFrontend {
  def apply()(implicit appContext: AppContext): SCFrontend = {
    new SCFrontend()
  }
}

class SCFrontend()(implicit val context: AppContext) extends BootstrapComponent {
  implicit val regionContext = RegionContext()
  implicit val keysContext = KeysContext()

  val regionSwitcher = RegionSwitcher()

  val folderContextRx = regionSwitcher.regionSelector.selectedRegion.fold(None: Option[FolderContext]) { case (oldCtxOpt, newIdOpt) ⇒
    oldCtxOpt.foreach { oldContext ⇒
      oldContext.scope.kill()
      oldContext.selected.kill()
      oldContext.updates.kill()
    }
    newIdOpt.map(FolderContext(_))
  }

  def render(md: ModifierT*) = {
    renderNavigationBar().render(md:_*)
  }

  def renderNavigationBar(): NavigationBar = {
    NavigationBar()
      .withBrand(img(src := "/favicon.png", maxHeight := 100.pct, display.inline, marginRight := 3.px), "shadowcloud")
      .withContentContainer(GridSystem.containerFluid(_))
      .withStyles(NavigationBarStyle.default, NavigationBarStyle.staticTop)
      .withTabs(
        NavigationTab(context.locale.foldersView, "folders", AppIcons.foldersView, renderFoldersPanel()),
        NavigationTab(context.locale.regionsView, "regions", AppIcons.regionsView, renderRegionsPanel())
      )
  }

  def renderRegionsPanel(): Tag = {
    RegionsStoragesPanel()
  }

  def renderFoldersPanel(): Tag = {
    val foldersPanelRx = folderContextRx.map[Frag] {
      case Some(folderContext) ⇒
        regionSwitcher.scopeSelector.selectedScope.foreach(folderContext.scope.update)
        val folderController = FolderController.forFolderContext(folderContext)
        val fileController = FileController.forFolderController(folderController)
        FoldersPanel()(context, folderContext, folderController, fileController)

      case None ⇒
        Bootstrap.noContent
    }

    val uploadFormRx = folderContextRx.map[Frag] {
      case Some(folderContext) ⇒
        val fileController = FileController.forFolderContext(folderContext)
        UploadForm()(context, folderContext, fileController).renderButton()

      case None ⇒
        Bootstrap.noContent
    }

    div(
      GridSystem.mkRow(ButtonGroup(ButtonGroupSize.small, regionSwitcher, uploadFormRx)),
      GridSystem.mkRow(foldersPanelRx)
    )
  }
}

