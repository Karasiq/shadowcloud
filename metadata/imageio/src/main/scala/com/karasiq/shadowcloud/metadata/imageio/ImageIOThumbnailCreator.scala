package com.karasiq.shadowcloud.metadata.imageio

import java.awt.image.BufferedImage

import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{Attributes, FlowShape}
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL}
import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataParser}
import com.karasiq.shadowcloud.metadata.imageio.utils.ImageIOResizer
import com.karasiq.shadowcloud.utils.{ByteStringOutputStream, Utils}

private[imageio] object ImageIOThumbnailCreator {
  def apply(config: Config): ImageIOThumbnailCreator = {
    new ImageIOThumbnailCreator(config)
  }
}

private[imageio] class ImageIOThumbnailCreator(config: Config) extends MetadataParser {
  protected object thumbnailCreatorSettings extends ConfigImplicits {
    val enabled = config.getBoolean("enabled")
    val extensions = config.getStringSet("extensions")
    val mimes = config.getStringSet("mimes")

    val size = config.getInt("size")
    val format = config.getString("format")
    val quality = config.getInt("quality")
  }

  def canParse(name: String, mime: String): Boolean = {
    thumbnailCreatorSettings.enabled &&
      (thumbnailCreatorSettings.mimes.contains(mime) || thumbnailCreatorSettings.extensions.contains(Utils.getFileExtension(name)))
  }

  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val flow = Flow.fromGraph(GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val loadImage = builder.add(Flow[ByteString].map { bytes ⇒
        ImageIOResizer.loadImage(bytes.toArray)
      })

      val createImageData = builder.add(Flow[BufferedImage].map { image ⇒
        Metadata(Some(Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.METADATA)),
          Metadata.Value.ImageData(Metadata.ImageData(image.getWidth, image.getHeight)))
      })

      val resizeImage = builder.add(Flow[BufferedImage].map { originalImage ⇒
        val image = ImageIOResizer.resize(originalImage, thumbnailCreatorSettings.size)
        val outputStream = ByteStringOutputStream()
        ImageIOResizer.compress(image, outputStream, thumbnailCreatorSettings.format, thumbnailCreatorSettings.quality)
        outputStream.toByteString
      })

      val createPreview = builder.add(Flow[ByteString].map { data ⇒
        Metadata(Some(Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.PREVIEW)),
          Metadata.Value.Preview(Metadata.Preview(thumbnailCreatorSettings.format, data)))
      })

      val readImage = builder.add(Flow[ByteString].fold(ByteString.empty)(_ ++ _))
      val processImage = builder.add(Broadcast[BufferedImage](2))
      val writeMetadata = builder.add(Concat[Metadata](2))

      readImage ~> loadImage ~> processImage

      processImage ~> createImageData ~> writeMetadata
      processImage ~> resizeImage ~> createPreview ~> writeMetadata

      FlowShape(readImage.in, writeMetadata.out)
    })
    flow.addAttributes(Attributes.name("imageioThumbnailCreator"))
  }
}
