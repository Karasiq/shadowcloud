package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.model.{File, Path, RegionId}
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common._
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.FileController
import org.scalajs.dom
import rx.{Rx, Var}
import scalaTags.all._

import scala.concurrent.Future

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController): UploadForm = {
    new UploadForm
  }

  private def isUrlList(text: String): Boolean =
    text.nonEmpty && text.lines.forall(_.matches("https?://[^\\s]+"))

  private   def newNoteName(text: String): String = {
    val firstLine = text.lines
      .map(_.trim)
      .find(_.nonEmpty)
      .getOrElse("")

    Utils
      .takeWords(firstLine, 50)
      .replaceAll("[*~]", "")
      .replaceAll("\\s+", " ")
      .trim
  }

  private def uploadNoteOrPage(regionId: RegionId, path: Path, text: String, name: String)(implicit appContext: AppContext): Future[Seq[File]] = {
    if (isUrlList(text)) {
      Future
        .sequence(text.lines.map(appContext.api.saveWebPage(regionId, path, _).map(Some(_)).recover { case _ => None }).toSeq)
        .map(_.flatten)
    } else {
      val fileName =
        if (name.isEmpty) newNoteName(text) + ".md"
        else if (name.contains(".")) name
        else name + ".md"
      appContext.api.uploadFile(regionId, path / fileName, text)._2.map(Seq(_))
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
    val fileName = Var("")

    val editor = {
      val editor = TextEditor.memoized("sc-text-upload") { editor ⇒
        editor.submitting() = true
        val future = UploadForm.uploadNoteOrPage(folderContext.regionId, folderContext.selected.now, editor.text.now, fileName.now)
        future.onComplete { result ⇒
          editor.submitting() = false
          result.toOption.toSeq.flatten.foreach { file ⇒
            editor.text() = ""
            fileName() = ""
            fileController.addFile(file)
            Toastr.success(s"${file.path.name} successfully uploaded to ${file.path.parent}")
          }
        }
      }

      val isUrls = editor.text.map(UploadForm.isUrlList)

      Form(
        editor,
        FormInput.text("", fileName.reactiveInput, placeholder := "test.md", AppComponents.disabledIf(isUrls)),
        small("Hint: links will be saved as web pages", isUrls.reactiveShow)
      )
    }

    val htmlEditor = {
      val formatSelector = FormInput.radioGroup(
        FormInput.radio("HTML", "html", "html", initialState = true),
        FormInput.radio("Markdown", "markdown", "markdown", initialState = true)
      )
      Form(
        Pell { editor =>
          editor.submitting() = true
          val extension = formatSelector.value.now match {
            case "html"     => "html"
            case "markdown" => "md"
          }

          val resultFileName =
            if (fileName.now.isEmpty) UploadForm.newNoteName(Pell.toMarkdown(editor.html.now)) + s".$extension"
            else if (fileName.now.endsWith(s".$extension")) fileName.now
            else fileName.now + s".$extension"

          val result =
            if (extension == "md") Pell.toMarkdown(editor.html.now)
            else editor.html.now

          val future = appContext.api.uploadFile(folderContext.regionId, folderContext.selected.now / resultFileName, result)._2
          future.foreach { file =>
            Toastr.success(s"${file.path.name} successfully uploaded to ${file.path.parent}")
            fileController.addFile(file)
            editor.html() = ""
            fileName() = ""
          }
          future.onComplete(_ => editor.submitting() = false)
        },
        formatSelector,
        FormInput.text("", fileName.reactiveInput, placeholder := "test.html")
      )
    }

    val navigation = Navigation.pills(
      NavigationTab(
        appContext.locale.uploadFiles,
        "upload-files",
        NoIcon,
        Rx(createForm(folderContext.regionId, folderContext.selected()))
      ),
      NavigationTab(appContext.locale.pasteText, "paste-text", NoIcon, editor),
      NavigationTab("HTML", "edit-html", NoIcon, htmlEditor)
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
