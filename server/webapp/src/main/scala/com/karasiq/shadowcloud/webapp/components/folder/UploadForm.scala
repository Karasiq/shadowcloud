package com.karasiq.shadowcloud.webapp.components.folder

import scala.annotation.tailrec
import scala.concurrent.Future

import org.scalajs.dom
import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.common.memory.SizeUnit
import com.karasiq.shadowcloud.model.{File, Path, RegionId}
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons, TextEditor}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.controllers.FileController

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController): UploadForm = {
    new UploadForm
  }

  private def newNoteName(text: String): String = {
    Utils.takeWords(text, 50).replaceAll("\\s+", " ").trim + ".md"
  }

  private def uploadNoteOrPage(regionId: RegionId, path: Path, text: String)(implicit appContext: AppContext): Future[File] = {
    if (text.matches("https?://[^\\s]+")) {
      appContext.api.saveWebPage(regionId, path, text)
    } else {
      appContext.api.uploadFile(regionId, path / newNoteName(text), text)._2
    }
  }
}

class UploadForm(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController) extends BootstrapHtmlComponent {

  import folderContext.{regionId, selected ⇒ selectedFolderPathRx}
  private[this] val maxUploads = 5
  private[this] val maxUploadsSize = SizeUnit.MB * 8
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
    Button(ButtonStyle.info)(AppIcons.upload, appContext.locale.uploadFiles, md, onclick := Callback.onClick { _ ⇒
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
    val (toUpload, rest) = {
      @tailrec def limitUploads(list: Seq[dom.File], maxSize: Long, currentCount: Int = 0): Seq[dom.File] = {
        val newListSize = list
          .take(currentCount + 1)
          .map(_.size.toLong).sum

        if (newListSize > maxSize)
          list.take(currentCount)
        else
          limitUploads(list, newListSize, currentCount + 1)
      }

      val nextUploadsLimit: Long = maxUploadsSize - uploading.now.map(_.size.toLong).sum

      val nextDownloads = uploadQueue.now
        .filterNot(uploading.now.contains)

      val toUpload = limitUploads(nextDownloads, nextUploadsLimit, if (uploading.now.isEmpty) 1 else 0)
        .take(maxUploads - uploading.now.length)

      (toUpload, nextDownloads.drop(toUpload.length))
    }

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

      fileFuture.foreach(fileController.addFile)
    }

    uploading() = uploading.now ++ toUpload
    uploadQueue() = rest
  }
}

