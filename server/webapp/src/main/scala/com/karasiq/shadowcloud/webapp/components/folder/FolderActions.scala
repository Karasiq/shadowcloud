package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Var

import com.karasiq.shadowcloud.model.{Path, RegionId}
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.folder.FolderTree.FolderController
import com.karasiq.shadowcloud.webapp.context.AppContext
import AppContext.JsExecutionContext

private[folder] object FolderActions {
  def apply(regionId: RegionId, path: Path)
           (implicit context: AppContext, fc: FolderController): FolderActions = {

    new FolderActions(regionId, path)
  }
}

private[folder] class FolderActions(regionId: RegionId, path: Path)
                   (implicit context: AppContext, fc: FolderController) extends BootstrapHtmlComponent {

  def renderTag(md: ModifierT*): TagT = {
    def showCreateFolderModal(): Unit = {
      val folderNameRx = Var("")
      val isFolderNameValid = folderNameRx.map(_.nonEmpty)

      Modal()
        .withTitle(context.locale.createFolder)
        .withBody(Form(FormInput.text(context.locale.name, folderNameRx.reactiveInputRead)))
        .withButtons(
          Modal.button(context.locale.submit, Modal.dismiss, isFolderNameValid.reactiveShow, onclick := Callback.onClick { _ ⇒
            context.api.createFolder(regionId, path / folderNameRx.now).foreach(fc.addFolder)
          }),
          Modal.closeButton(context.locale.close)
        )
        .show()
    }

    def showDeleteFolderModal(): Unit = {
      Modal()
        .withTitle(context.locale.deleteFolder)
        .withBody(context.locale.deleteFolderConfirmation(path))
        .withButtons(
          Modal.button(context.locale.submit, Modal.dismiss, onclick := Callback.onClick { _ ⇒
            context.api.deleteFolder(regionId, path).foreach(fc.deleteFolder)
          }),
          Modal.closeButton(context.locale.close)
        )
        .show()
    }

    def renderIcon(icon: IconModifier, action: () ⇒ Unit): TagT = {
      a(href := "#", icon, onclick := Callback.onClick(_ ⇒ action()))
    }

    span(
      renderIcon(AppIcons.refresh, fc.update),
      renderIcon(AppIcons.create, showCreateFolderModal),
      renderIcon(AppIcons.delete, showDeleteFolderModal),
      md
    )
  }
}

