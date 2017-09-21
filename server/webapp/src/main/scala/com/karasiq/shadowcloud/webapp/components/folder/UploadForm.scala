package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Rx

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object UploadForm {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext): UploadForm = {
    new UploadForm
  }
}

class UploadForm(implicit appContext: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  import folderContext.{regionId, selected ⇒ selectedFolderPathRx}
  private[this] val uploadProgressBars = div().render

  def renderTag(md: ModifierT*): TagT = {
    val uploadInput = FormInput.file(appContext.locale.file, multiple, md, onchange := Callback.onInput { input ⇒
      input.files.foreach { inputFile ⇒
        val parent: Path = selectedFolderPathRx.now
        val (progressRx, fileFuture) = appContext.api.uploadFile(regionId, parent / inputFile.name, inputFile)
        val progressBar = renderProgressBar(inputFile.name, progressRx).render

        uploadProgressBars.appendChild(progressBar)
        fileFuture.onComplete { _ ⇒
          // progressBar.outerHTML = ""
          uploadProgressBars.removeChild(progressBar)
          progressRx.kill()
        }

        fileFuture.foreach { file ⇒
          folderContext.update(parent)
          // dom.window.alert(file.toString)
          input.form.reset()
        }
      }
    })

    div(
      GridSystem.mkRow(Form(uploadInput)),
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
}

