package com.karasiq.shadowcloud.metadata.imageio

import java.awt.image.BufferedImage

import scala.language.postfixOps

import akka.NotUsed
import akka.stream.FlowShape
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL}
import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}
import Metadata.Tag.Disposition
import com.karasiq.shadowcloud.metadata.config.MetadataParserConfig
import com.karasiq.shadowcloud.metadata.imageio.utils.ImageIOResizer
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.utils.ByteStringOutputStream
import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._

private[imageio] object ImageIOThumbnailCreator {
  val PluginId = "imageio"
  val ParserId = "thumbnail"

  def apply(config: Config): ImageIOThumbnailCreator = {
    new ImageIOThumbnailCreator(config)
  }
}

private[imageio] class ImageIOThumbnailCreator(config: Config) extends MetadataParser {
  import ImageIOThumbnailCreator.{ParserId, PluginId}

  protected object thumbnailSettings extends ConfigImplicits {
    val parserConfig = MetadataParserConfig(config)
    val sizeLimit = config.getBytesInt("size-limit")

    val size = config.getInt("size")
    val format = config.getString("format")
    val quality = config.getInt("quality")
  }

  def canParse(name: String, mime: String): Boolean = {
    thumbnailSettings.parserConfig.canParse(name, mime)
  }

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val flowGraph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val loadImage = builder.add(Flow[ByteString].map { bytes ⇒
        ImageIOResizer.loadImage(bytes.toArrayUnsafe)
      })

      val createImageData = builder.add(Flow[BufferedImage].map { image ⇒
        Metadata(Some(Metadata.Tag(PluginId, ParserId, Disposition.METADATA)),
          Metadata.Value.ImageData(Metadata.ImageData(image.getWidth, image.getHeight)))
      })

      val resizeImage = builder.add(Flow[BufferedImage].map { originalImage ⇒
        val image = ImageIOResizer.resize(originalImage, thumbnailSettings.size)
        val outputStream = ByteStringOutputStream()
        ImageIOResizer.compress(image, outputStream, thumbnailSettings.format, thumbnailSettings.quality)
        outputStream.toByteString
      })

      val createPreview = builder.add(Flow[ByteString].map { data ⇒
        Metadata(Some(Metadata.Tag(PluginId, ParserId, Disposition.PREVIEW)),
          Metadata.Value.Thumbnail(Metadata.Thumbnail(thumbnailSettings.format, data)))
      })

      val readImage = builder.add(Flow[ByteString].fold(ByteString.empty)(_ ++ _))
      val processImage = builder.add(Broadcast[BufferedImage](2))
      val writeMetadata = builder.add(Concat[Metadata](2))

      readImage ~> loadImage ~> processImage

      processImage ~> createImageData ~> writeMetadata
      processImage ~> resizeImage ~> createPreview ~> writeMetadata

      FlowShape(readImage.in, writeMetadata.out)
    }

    Flow[ByteString]
      .via(ByteStreams.limit(thumbnailSettings.sizeLimit))
      .via(flowGraph)
      .named("imageioThumbnail")
  }
}
