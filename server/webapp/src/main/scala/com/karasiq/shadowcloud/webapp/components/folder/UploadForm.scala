package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.model.{File, Path, RegionId}
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common._
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.FileController
import org.scalajs.dom
import rx.Rx
import scalaTags.all._

import scala.concurrent.Future

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController): UploadForm = {
    new UploadForm
  }

  private def isUrlList(text: String): Boolean =
    text.lines.forall(_.matches("https?://[^\\s]+"))

  private def uploadNoteOrPage(regionId: RegionId, path: Path, text: String)(implicit appContext: AppContext): Future[Seq[File]] = {
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

    if (isUrlList(text)) {
      Future
        .sequence(text.lines.map(appContext.api.saveWebPage(regionId, path, _).map(Some(_)).recover { case _ => None }).toSeq)
        .map(_.flatten)
    } else {
      appContext.api.uploadFile(regionId, path / newNoteName(text), text)._2.map(Seq(_))
    }
  }
}

class UploadForm(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController) extends BootstrapHtmlComponent {

  private[this] final case class UploadRequest(regionId: RegionId, path: Path, file: dom.File)

  private[this] val formMap = collection.mutable.Map.empty[Path, ElementT]

  private[this] def createForm(regionId: RegionId, path: Path) = {
    formMap.getOrElseUpdate(
      path,
      Form(
        action := "/",
        `class` := "dropzone",
        Dropzone(folderContext.regionId, () => path, { file =>
          folderContext.update(path)
        })
      ).render
    )
  }

  def renderTag(md: ModifierT*): TagT = {
    val editor = TextEditor.memoized("sc-text-upload") { editor ⇒
      editor.submitting() = true
      val future = UploadForm.uploadNoteOrPage(folderContext.regionId, folderContext.selected.now, editor.value.now)
      future.onComplete { result ⇒
        editor.submitting() = false
        result.toOption.toSeq.flatten.foreach { file ⇒
          editor.value() = ""
          fileController.addFile(file)
          Toastr.success(s"${file.path.name} successfully uploaded to ${file.path.parent}")
        }
      }
    }

    val navigation = Navigation.pills(
      NavigationTab(
        appContext.locale.uploadFiles,
        "upload-files",
        NoIcon,
        Rx(createForm(folderContext.regionId, folderContext.selected()))
      ),
      NavigationTab(appContext.locale.pasteText, "paste-text", NoIcon, editor)
    )

    GridSystem.mkRow(navigation)
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
