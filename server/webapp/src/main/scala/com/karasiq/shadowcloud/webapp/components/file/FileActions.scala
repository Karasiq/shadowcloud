package com.karasiq.shadowcloud.webapp.components.file

import scala.concurrent.Future

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import akka.Done
import rx.{Rx, Var}

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object FileActions {
  def apply(file: File, useId: Boolean = false)(implicit context: AppContext, folderContext: FolderContext): FileActions = {
    new FileActions(file, useId)
  }
}

final class FileActions(file: File, useId: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  private[this] val deleted = Var(false)

  def renderTag(md: ModifierT*): TagT = {
    div(
      renderDownloadLink(),
      renderRename(),
      renderDelete(),
      if (TextFileView.canBeViewed(file)) renderEditor() else (),
      if (MediaFileView.canBeViewed(file)) renderPlayer() else ()
    )
  }

  private[this] def renderDownloadLink(): TagT = {
    FileDownloadLink(file, useId)(AppIcons.download, Bootstrap.nbsp,
      context.locale.downloadFile, attr("download") := file.path.name)
  }

  private[this] def renderEditor(): TagT = {
    renderAction(context.locale.viewTextFile, AppIcons.viewText, onclick := Callback.onClick { _ ⇒
      Modal()
        .withTitle(context.locale.viewTextFile)
        .withBody(TextFileView(file))
        .withButtons(AppComponents.modalClose())
        .withDialogStyle(ModalDialogSize.large)
        .show(backdrop = false)
    })
  }

  private[this] def renderPlayer(): TagT = {
    val opened = Var(false)
    div(
      renderAction(context.locale.playFile, AppIcons.play, onclick := Callback.onClick(_ ⇒ opened() = !opened.now)),
      Rx[Frag](if (opened()) MediaFileView(file, useId) else ())
    )
  }

  private[this] def renderRename(): TagT = {
    def doRename(newName: String): Future[Set[File]] = {
      def doCopy() = if (useId) {
        context.api.copyFile(folderContext.regionId, file, file.path.withName(newName), folderContext.scope.now)
      } else {
        context.api.copyFiles(folderContext.regionId, file.path, file.path.withName(newName), folderContext.scope.now)
      }

      def doDelete() = if (useId) {
        context.api.deleteFile(folderContext.regionId, file).map(Set(_))
      } else {
        context.api.deleteFiles(folderContext.regionId, file.path)
      }

      for (Done ← doCopy(); deletedFiles ← doDelete())
        yield deletedFiles
    }

    def onRename(newName: String): Unit = {
      doRename(newName).foreach { _ ⇒
        folderContext.update(file.path.parent)
        this.deleted() = true
      }
    }

    renderAction(context.locale.rename, AppIcons.rename, onclick := Callback.onClick { _ ⇒
      val newNameRx = Var(file.path.name)
      Modal()
        .withTitle(context.locale.rename)
        .withButtons(
          AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ onRename(newNameRx.now))),
          AppComponents.modalClose()
        )
        .withBody(Form(FormInput.text(context.locale.name, newNameRx.reactiveInput)))
        .show()
    })
  }

  private[this] def renderDelete(): TagT = {
    def deleteFile(): Unit = {
      val result = if (useId) {
        context.api.deleteFile(folderContext.regionId, file).map(Set(_))
      } else {
        context.api.deleteFiles(folderContext.regionId, file.path)
      }

      result.foreach { _ ⇒
        deleted() = true 
        folderContext.update(file.path.parent)
      }
    }

    div(
      Rx(if (deleted()) textDecoration.`line-through` else textDecoration.none).auto,
      renderAction(context.locale.deleteFile, AppIcons.delete, onclick := Callback.onClick(_ ⇒ if (!deleted.now) deleteFile()))
    )
  }

  private[this] def renderAction(title: ModifierT, icon: IconModifier, linkMd: ModifierT*): TagT = {
    div(AppComponents.iconLink(title, icon, linkMd:_*))
  }
}

