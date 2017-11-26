package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import rx.{Rx, Var}

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons, TextEditor}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext): UploadForm = {
    new UploadForm
  }

  private def newNoteName(text: String): String = {
    Utils.takeWords(text, 50).replaceAll("\\s+", " ").trim + ".md"
  }
}

class UploadForm(implicit appContext: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  import folderContext.{regionId, selected ⇒ selectedFolderPathRx}
  private[this] val uploadProgressBars = div().render
  private[this] val uploadQueue = Var(List.empty[dom.File])
  private[this] val uploading = Var(List.empty[dom.File])

  uploadQueue.triggerLater(processQueue())
  uploading.triggerLater(processQueue())

  def renderTag(md: ModifierT*): TagT = {
    val uploadInput = FormInput.file(appContext.locale.file, multiple, md, onchange := Callback.onInput { input ⇒
      uploadQueue() = uploadQueue.now ++ input.files.toList
      input.form.reset()
    })

    val editor = TextEditor { editor ⇒
      editor.submitting() = true
      val path = folderContext.selected.now / UploadForm.newNoteName(editor.value.now)
      val (_, future) = appContext.api.uploadFile(folderContext.regionId, path, editor.value.now)
      future.onComplete { result ⇒
        editor.submitting() = false
        result.foreach { file ⇒
          editor.value() = ""
          folderContext.update(file.path.parent)
        }
      }
    }

    val navigation = Navigation.pills(
      NavigationTab(appContext.locale.uploadFiles, "upload-files", NoIcon, Form(uploadInput)),
      NavigationTab(appContext.locale.pasteText, "paste-text", NoIcon, editor)
    )

    div(
      GridSystem.mkRow(navigation),
      GridSystem.mkRow(uploadProgressBars)
    )
  }

  def renderModal(md: ModifierT*): Modal = {
    Modal(appContext.locale.uploadFiles, renderTag(md:_*), AppComponents.modalClose(), dialogStyle = ModalDialogSize.large)
  }

  def renderButton(md: ModifierT*): TagT = {
    Button(ButtonStyle.info)(AppIcons.upload, Bootstrap.nbsp, appContext.locale.uploadFiles, md, onclick := Callback.onClick { _ ⇒
      renderModal().show()
    })
  }

  private[this] def renderProgressBar(fileName: String, progress: Rx[Int]): TagT = {
    val styles = Seq(ProgressBarStyle.success, ProgressBarStyle.striped, ProgressBarStyle.animated)
    div(
      div(b(fileName)),
      ProgressBar.withLabel(progress).renderTag(styles:_*),
      hr
    )
  }

  private[this] def processQueue() = {
    val toUploadN = 5 - uploading.now.length
    val (toUpload, rest) = uploadQueue.now
      .filterNot(uploading.now.contains)
      .splitAt(toUploadN)

    toUpload.foreach { file ⇒
      val parent: Path = selectedFolderPathRx.now
      val (progressRx, fileFuture) = appContext.api.uploadFile(regionId, parent / file.name, file)
      val progressBar = renderProgressBar(file.name, progressRx).render

      uploadProgressBars.appendChild(progressBar)
      fileFuture.onComplete { _ ⇒
        uploading() = uploading.now.filterNot(_ == file)
        uploadProgressBars.removeChild(progressBar)
        progressRx.kill()
      }

      fileFuture.foreach(_ ⇒ folderContext.update(parent))
    }

    uploading() = uploading.now ++ toUpload
    uploadQueue() = rest
  }
}

