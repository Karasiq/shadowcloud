package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Rx

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils
import com.karasiq.videojs.{VideoJSBuilder, VideoSource}

object MediaFileView {
  private[this] val AudioFormats = Set("mp3", "flac", "ogg", "wav", "aac")
  private[this] val ImageFormats = Set("jpg", "jpeg", "png", "webp", "bmp", "gif")
  private[this] val VideoFormats = Set("webm", "mp4", "ogv")

  def apply(file: File, useId: Boolean = false)(implicit context: AppContext, folderContext: FolderContext): MediaFileView = {
    new MediaFileView(file, useId)
  }

  def canBeViewed(file: File): Boolean = {
    testFileFormat(file, VideoFormats ++ AudioFormats ++ ImageFormats)
  }

  def isImageFile(file: File): Boolean = {
    testFileFormat(file, ImageFormats)
  }

  def isVideoFile(file: File): Boolean = {
    testFileFormat(file, VideoFormats)
  }

  def isAudioFile(file: File): Boolean = {
    testFileFormat(file, AudioFormats)
  }

  private[this] def testFileFormat(file: File, formats: Set[String]): Boolean = {
    val extension = Utils.getFileExtensionLowerCase(file.path.name)
    formats.contains(extension)
  }
}

class MediaFileView(file: File, useId: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val fileUrl = RxUtils.getDownloadLinkRx(file, useId)

    def renderImage() = {
      val baseImage = img(Rx(src := fileUrl()).auto, Bootstrap.image.responsive)
      div(baseImage(onclick := Callback.onClick { _ ⇒
        Modal()
          .withTitle(context.locale.image)
          .withBody(baseImage)
          .withButtons(AppComponents.modalClose())
          .withDialogStyle(ModalDialogSize.large)
          .show()
      }))
    }

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
      if (MediaFileView.isAudioFile(file))
        renderAudioPlayer()
      else if (MediaFileView.isImageFile(file))
        renderImage()
      else 
        renderVideoPlayer()
    }

    div(renderPlayer())
  }
}

