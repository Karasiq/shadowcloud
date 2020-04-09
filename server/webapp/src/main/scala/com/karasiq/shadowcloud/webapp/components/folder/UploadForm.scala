package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.common.memory.SizeUnit
import com.karasiq.shadowcloud.model.{File, Path, RegionId}
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons, Dropzone, TextEditor}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.FileController
import org.scalajs.dom
import rx.{Rx, Var}
import scalaTags.all._

import scala.annotation.tailrec
import scala.concurrent.Future

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController): UploadForm = {
    new UploadForm
  }

  private def uploadNoteOrPage(regionId: RegionId, path: Path, text: String)(implicit appContext: AppContext): Future[File] = {
    def newNoteName(text: String): String = {
      val firstLine = text.lines
        .map(_.trim)
        .find(_.nonEmpty)
        .getOrElse("")

      val conciseName = Utils
        .takeWords(firstLine, 50)
        .replaceAll("\\s+", " ")
        .trim

      conciseName + ".md"
    }

    if (text.matches("https?://[^\\s]+")) {
      appContext.api.saveWebPage(regionId, path, text)
    } else {
      appContext.api.uploadFile(regionId, path / newNoteName(text), text)._2
    }
  }
}

class UploadForm(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController) extends BootstrapHtmlComponent {

  private[this] final case class UploadRequest(regionId: RegionId, path: Path, file: dom.File)

  private[this] val maxUploads         = 5
  private[this] val maxUploadsSize     = SizeUnit.MB * 8
  private[this] val uploadProgressBars = div().render
  private[this] val uploadQueue        = Var(List.empty[UploadRequest])
  private[this] val uploading          = Var(List.empty[UploadRequest])

  private[this] lazy val renderedForm = Form(
    action := "/",
    `class` := "dropzone",
    Dropzone(folderContext.regionId, () => folderContext.selected.now, _ => folderContext.update(folderContext.selected.now))
  ).render

  def renderTag(md: ModifierT*): TagT = {
    val editor = TextEditor.memoized("sc-text-upload") { editor ⇒
      editor.submitting() = true
      val future = UploadForm.uploadNoteOrPage(folderContext.regionId, folderContext.selected.now, editor.value.now)
      future.onComplete { result ⇒
        editor.submitting() = false
        result.foreach { file ⇒
          editor.value() = ""
          fileController.addFile(file)
        }
      }
    }

    val navigation = Navigation.pills(
      NavigationTab(
        appContext.locale.uploadFiles,
        "upload-files",
        NoIcon,
        renderedForm
      ),
      NavigationTab(appContext.locale.pasteText, "paste-text", NoIcon, editor)
    )

    div(
      GridSystem.mkRow(navigation),
      GridSystem.mkRow(uploadProgressBars)
    )
  }

  def renderModal(md: ModifierT*): Modal = {
    Modal(appContext.locale.uploadFiles, renderTag(md: _*), AppComponents.modalClose(), dialogStyle = ModalDialogSize.large)
  }

  def renderButton(md: ModifierT*): TagT = {
    Button(ButtonStyle.info)(AppIcons.upload, appContext.locale.uploadFiles, md, onclick := Callback.onClick { _ ⇒
      renderModal().show()
    })
  }
}
