package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Var

import com.karasiq.shadowcloud.model.{Path, RegionId}
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.components.folder.FolderTree.FolderController
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import AppContext.JsExecutionContext

private[folder] object FolderActions {
  def apply(regionId: RegionId, path: Path)
           (implicit appContext: AppContext, folderContext: FolderContext, folderController: FolderController): FolderActions = {

    new FolderActions(regionId, path)
  }
}

private[folder] class FolderActions(regionId: RegionId, path: Path)
                                   (implicit context: AppContext, folderContext: FolderContext, folderController: FolderController) extends BootstrapHtmlComponent {

  def renderTag(md: ModifierT*): TagT = {
    def showCreateFolderModal(): Unit = {
      val folderNameRx = Var("")
      val isFolderNameValid = folderNameRx.map(_.nonEmpty)

      Modal()
        .withTitle(context.locale.createFolder)
        .withBody(Form(FormInput.text(context.locale.name, folderNameRx.reactiveInputRead)))
        .withButtons(
          Modal.button(context.locale.submit, Modal.dismiss, isFolderNameValid.reactiveShow, onclick := Callback.onClick { _ ⇒
            context.api.createFolder(regionId, path / folderNameRx.now).foreach(folderController.addFolder)
          }),
          AppComponents.modalClose()
        )
        .show()
    }

    def showRenameFolderModal(): Unit = {
      val folderNameRx = Var(path.name)
      val isFolderNameValid = folderNameRx.map(_.nonEmpty)

      Modal()
        .withTitle(context.locale.rename)
        .withBody(Form(FormInput.text(context.locale.name, folderNameRx.reactiveInput)))
        .withButtons(
          Modal.button(context.locale.submit, Modal.dismiss, isFolderNameValid.reactiveShow, onclick := Callback.onClick { _ ⇒
            for {
              newFolder ← context.api.copyFolder(regionId, path, path.withName(folderNameRx.now), folderContext.scope.now)
              oldFolder ← context.api.deleteFolder(regionId, path)
            } {
              folderController.deleteFolder(oldFolder)
              folderController.addFolder(newFolder)
            }
          }),
          AppComponents.modalClose()
        )
        .show()
    }

    def showDeleteFolderModal(): Unit = {
      Modal()
        .withTitle(context.locale.deleteFolder)
        .withBody(context.locale.deleteFolderConfirmation(path))
        .withButtons(
          AppComponents.modalSubmit(onclick := Callback.onClick { _ ⇒
            context.api.deleteFolder(regionId, path).foreach(folderController.deleteFolder)
          }),
          AppComponents.modalClose()
        )
        .show()
    }

    def renderIcon(icon: IconModifier, action: () ⇒ Unit): TagT = {
      a(href := "#", icon, onclick := Callback.onClick(_ ⇒ action()))
    }

    span(
      // renderIcon(AppIcons.refresh, fc.update),
      renderIcon(AppIcons.create, showCreateFolderModal),
      if (!path.isRoot) renderIcon(AppIcons.rename, showRenameFolderModal) else (),
      if (!path.isRoot) renderIcon(AppIcons.delete, showDeleteFolderModal) else (),
      md
    )
  }
}

