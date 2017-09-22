package com.karasiq.shadowcloud.webapp.components

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.folder.{FoldersPanel, UploadForm}
import com.karasiq.shadowcloud.webapp.components.keys.KeysContext
import com.karasiq.shadowcloud.webapp.components.region.{RegionContext, RegionsStoragesPanel, RegionSwitcher}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

object SCFrontend {
  def apply()(implicit appContext: AppContext): SCFrontend = {
    new SCFrontend()
  }
}

class SCFrontend()(implicit val appContext: AppContext) extends BootstrapComponent {
  implicit val regionContext = RegionContext()
  implicit val keysContext = KeysContext()

  val regionSwitcher = RegionSwitcher()

  def render(md: ModifierT*) = {
    renderNavigationBar().render(md:_*)
  }

  def renderNavigationBar(): NavigationBar = {
    NavigationBar()
      .withBrand("shadowcloud")
      .withContentContainer(GridSystem.containerFluid(_))
      .withStyles(NavigationBarStyle.default, NavigationBarStyle.staticTop)
      .withTabs(
        NavigationTab(appContext.locale.foldersView, "folders", AppIcons.foldersView, renderFoldersPanel()),
        NavigationTab(appContext.locale.regionsView, "regions", AppIcons.regionsView, renderRegionsPanel())
      )
  }

  def renderRegionsPanel(): Tag = {
    RegionsStoragesPanel()
  }

  def renderFoldersPanel(): Tag = {
    val folderContextRx = regionSwitcher.regionSelector.selectedRegion.fold(None: Option[FolderContext]) { case (oldCtxOpt, newIdOpt) ⇒
      oldCtxOpt.foreach { oldContext ⇒
        oldContext.scope.kill()
        oldContext.selected.kill()
        oldContext.updates.kill()
      }
      newIdOpt.map(FolderContext(_))
    }

    val foldersPanelRx = folderContextRx.map[Frag] {
      case Some(folderContext) ⇒
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

    div(
      GridSystem.mkRow(
        regionSwitcher,
        uploadFormRx
      ),
      GridSystem.mkRow(foldersPanelRx)
    )
  }
}

