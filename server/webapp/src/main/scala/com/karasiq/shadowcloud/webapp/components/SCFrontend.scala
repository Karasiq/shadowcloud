package com.karasiq.shadowcloud.webapp.components

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.folder.FoldersPanel
import com.karasiq.shadowcloud.webapp.components.keys.KeysContext
import com.karasiq.shadowcloud.webapp.components.region.{RegionContext, RegionSwitcher, RegionsStoragesPanel}
import com.karasiq.shadowcloud.webapp.components.themes.ThemeSelector
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.{FileController, FolderController}
import com.karasiq.shadowcloud.webapp.utils.RxLocation
import org.scalajs.dom
import scalaTags.all._

import scala.util.control.NonFatal

object SCFrontend {
  def apply()(implicit appContext: AppContext): SCFrontend = {
    new SCFrontend()
  }
}

class SCFrontend()(implicit val context: AppContext) {
  implicit val regionContext = RegionContext()
  implicit val keysContext   = KeysContext()

  val regionSwitcher = RegionSwitcher()

  val folderContextRx = regionSwitcher.regionSelector.selectedRegion.fold(None: Option[FolderContext]) { case (oldCtxOpt, newIdOpt) ⇒
    oldCtxOpt.foreach { oldContext ⇒
      oldContext.scope.kill()
      oldContext.selected.kill()
      oldContext.updates.kill()
    }
    newIdOpt.map(FolderContext(_))
  }

  val themeSelector = ThemeSelector()

  def init(): Unit = {
    // Themes
    try {
      val themeCss = dom.document.head.querySelector("#sc-theme")
      themeSelector.linkModifier.applyTo(themeCss)
    } catch {
      case NonFatal(_) ⇒
        themeSelector.applyTo(dom.document.head)
    }

    // Frontend
    renderNavigationBar().applyTo(dom.document.body)

    // Context binding
    val contextBinding = SCContextBinding()
    contextBinding.bindToFrontend(this)
    contextBinding.bindToString(RxLocation().hash)
    contextBinding.toTitleRx.foreach(title ⇒ dom.document.title = s"shadowcloud - $title")
  }

  def renderNavigationBar(): NavigationBar = {
    NavigationBar()
      .withBrand(
        img(src := "/favicon.png", maxHeight := 100.pct, display.inline, marginRight := 3.px),
        "shadowcloud",
        onclick := Callback.onClick(_ ⇒ themeSelector.nextTheme())
      )
      .withContentContainer(GridSystem.containerFluid(_, "sc-main-container".addClass))
      .withStyles(NavigationBarStyle.default, NavigationBarStyle.staticTop)
      .withTabs(
        NavigationTab(context.locale.foldersView, "folders", AppIcons.foldersView, renderFoldersPanel()),
        NavigationTab(context.locale.regionsView, "regions", AppIcons.regionsView, renderRegionsPanel()),
        NavigationTab(context.locale.logs, "logs", AppIcons.logs, LogPanel()),
        NavigationTab(context.locale.challenges, "challengees", AppIcons.rename, ChallengePanel())
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
        val fileController   = FileController.forFolderController(folderController)
        FoldersPanel()(context, folderContext, folderController, fileController)

      case None ⇒
        Bootstrap.noContent
    }

    div(
      GridSystem.mkRow(regionSwitcher),
      GridSystem.mkRow(foldersPanelRx)
    )
  }
}
