package com.karasiq.shadowcloud.metadata.javacv

import java.io.InputStream

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.Config
import org.bytedeco.javacv.{FFmpegFrameGrabber, OpenCVFrameConverter}

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.utils.BlockingMetadataParser
import com.karasiq.shadowcloud.utils.Utils

private[javacv] object FFMPEGThumbnailCreator {
  def apply(config: Config): FFMPEGThumbnailCreator = {
    new FFMPEGThumbnailCreator(config)
  }
}

private[javacv] class FFMPEGThumbnailCreator(config: Config) extends BlockingMetadataParser {
  protected object settings {
    import com.karasiq.common.configs.ConfigImplicits._
    val enabled = config.getBoolean("enabled")
    val extensions = config.getStringSet("extensions")
    val thumbnailSize = config.getInt("thumbnail-size")
    val thumbnailQuality = config.getInt("thumbnail-quality")
  }

  def canParse(name: String, mime: String) = {
    def extension = Utils.getFileExtensionLowerCase(name)
    settings.enabled && settings.extensions.contains(extension)
  }

  protected def parseMetadata(name: String, mime: String, inputStream: InputStream) = {
    val grabber = new FFmpegFrameGrabber(inputStream)

    try {
      grabber.start()

      val converter = new OpenCVFrameConverter.ToIplImage()
      val image = converter.convert(grabber.grabImage())
      val thumb = OpenCVThumbnailCreator.resizeIplImage(image, settings.thumbnailSize, settings.thumbnailSize)
      val result = try {
        val jpegBytes = ByteString(JavaCV.asJpeg(thumb, settings.thumbnailQuality))
        Metadata(Some(Metadata.Tag("javacv", "ffmpeg", Metadata.Tag.Disposition.PREVIEW)),
          Metadata.Value.Thumbnail(Metadata.Thumbnail("jpeg", jpegBytes)))
      } finally {
        image.release()
        thumb.release()
      }

      Source.single(result)
    } finally grabber.stop()
  }
}
