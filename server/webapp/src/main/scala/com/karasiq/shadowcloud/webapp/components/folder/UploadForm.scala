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
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons, Dropzone, TextEditor}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.controllers.FileController

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext, fileController: FileController): UploadForm = {
    new UploadForm
  }

  private def newNoteName(text: String): String = {
    val conciseName = Utils.takeWords(text, 50)
      .replaceAll("\\s+", " ")
      .trim

    conciseName + ".md"
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

  private[this] final case class UploadRequest(regionId: RegionId, path: Path, file: dom.File)

  private[this] val maxUploads = 5
  private[this] val maxUploadsSize = SizeUnit.MB * 8
  private[this] val uploadProgressBars = div().render
  private[this] val uploadQueue = Var(List.empty[UploadRequest])
  private[this] val uploading = Var(List.empty[UploadRequest])

  uploadQueue.triggerLater(processQueue())
  uploading.triggerLater(processQueue())

  def renderTag(md: ModifierT*): TagT = {
//    val uploadInput = FormInput.file(appContext.locale.file, multiple, md, onchange := Callback.onInput { input ⇒
//
//      input.form.reset()
//    })

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
      NavigationTab(appContext.locale.uploadFiles, "upload-files", NoIcon, Form(action := "/", `class` := "dropzone", Dropzone { file =>
        // Preserve context at click time
        uploadQueue() = uploadQueue.now :+ UploadRequest(folderContext.regionId, folderContext.selected.now, file)
      })),
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

  private[this] def processQueue(): Unit = {
    val (toUpload, rest) = {
      @tailrec def limitUploads(list: Seq[UploadRequest], maxSize: Long, currentCount: Int = 0): Seq[UploadRequest] = {
        val newListSize = list
          .take(currentCount + 1)
          .map(_.file.size.toLong).sum

        if (newListSize > maxSize || currentCount >= list.length)
          list.take(currentCount)
        else
          limitUploads(list, newListSize, currentCount + 1)
      }

      val nextUploadsLimit: Long = maxUploadsSize - uploading.now.map(_.file.size.toLong).sum

      val nextDownloads = uploadQueue.now
        .filterNot(uploading.now.contains)

      val toUpload = limitUploads(nextDownloads, nextUploadsLimit, if (uploading.now.isEmpty) 1 else 0)
        .take(maxUploads - uploading.now.length)

      (toUpload, nextDownloads.drop(toUpload.length))
    }

    if (toUpload.nonEmpty) {
      toUpload.foreach { request ⇒
        val (progressRx, fileFuture) = appContext.api.uploadFile(request.regionId, request.path / request.file.name, request.file)
        val progressBar = renderProgressBar(request.file.name, progressRx).render

        uploadProgressBars.appendChild(progressBar)
        fileFuture.onComplete { _ ⇒
          uploading() = uploading.now.filterNot(_ == request)
          uploadProgressBars.removeChild(progressBar)
          progressRx.kill()
        }

        fileFuture.foreach(fileController.addFile)
      }

      uploading() = uploading.now ++ toUpload
      uploadQueue() = rest
    }
  }
}

