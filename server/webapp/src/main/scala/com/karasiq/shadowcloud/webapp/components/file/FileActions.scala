package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.RxUtils
import com.karasiq.videojs.{VideoJSBuilder, VideoSource}

object FileActions {
  private[this] val AudioFormats = Set("mp3", "flac", "ogg", "wav", "aac")
  private[this] val VideoFormats = Set("webm", "mp4", "ogv")

  def apply(file: File, useId: Boolean = false)(implicit context: AppContext, folderContext: FolderContext): FileActions = {
    new FileActions(file, useId)
  }

  private def isMediaFile(file: File): Boolean = {
    testFileFormat(file, VideoFormats ++ AudioFormats)
  }

  private def isVideoFile(file: File): Boolean = {
    testFileFormat(file, VideoFormats)
  }

  private def isAudioFile(file: File): Boolean = {
    testFileFormat(file, AudioFormats)
  }

  private[this] def testFileFormat(file: File, formats: Set[String]): Boolean = {
    val extension = Utils.getFileExtensionLowerCase(file.path.name)
    formats.contains(extension)
  }
}

final class FileActions(file: File, useId: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {

    div(
      renderDownloadLink(),
      renderDelete(),
      if (FileActions.isMediaFile(file)) renderPlayer() else ()
    )
  }

  private[this] def renderDownloadLink(): TagT = {
    FileDownloadLink(file, useId)(AppIcons.download, Bootstrap.nbsp,
      context.locale.downloadFile, attr("download") := file.path.name)
  }

  private[this] def renderPlayer(): TagT = {
    val fileUrl = RxUtils.getDownloadLinkRx(file, useId)

    def renderVideoPlayer() = {
      div(Rx {
        VideoJSBuilder()
          .sources(VideoSource("", fileUrl()))
          .autoplay(true)
          .fluid(true)
          .loop(true)
          .build()
      })
    }

    def renderAudioPlayer() = {
      audio(
        Rx(src := fileUrl()).auto,
        attr("controls").empty,
        attr("loop") := true,
        attr("autoplay") := true
      )
    }

    def renderPlayer(): Frag = {
      if (FileActions.isAudioFile(file))
        renderAudioPlayer()
      else
        renderVideoPlayer()
    }

    val opened = Var(false)
    div(
      renderAction(context.locale.playFile, AppIcons.play, onclick := Callback.onClick(_ ⇒ opened() = !opened.now)),
      Rx[Frag](if (opened()) renderPlayer() else ())
    )
  }

  private[this] def renderDelete(): TagT = {
    val deleted = Var(false)

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

