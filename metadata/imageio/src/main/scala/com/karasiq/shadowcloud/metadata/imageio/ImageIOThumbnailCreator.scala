package com.karasiq.shadowcloud.metadata.imageio

import java.awt.image.BufferedImage

import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{Attributes, FlowShape}
import akka.stream.scaladsl.{Flow, GraphDSL}
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
  protected object thumbnailCreatorConfig extends ConfigImplicits {
    val enabled = config.getBoolean("enabled")
    val extensions = config.getStringSet("extensions")
    val mimes = config.getStringSet("mimes")

    val size = config.getInt("size")
    val format = config.getString("format")
    val quality = config.getInt("quality")
  }

  def canParse(name: String, mime: String): Boolean = {
    thumbnailCreatorConfig.enabled &&
      (thumbnailCreatorConfig.mimes.contains(mime) || thumbnailCreatorConfig.extensions.contains(Utils.getFileExtension(name)))
  }
  
  def parseMetadata(name: String, mime: String): Flow[ByteString, Metadata, NotUsed] = {
    val flow = Flow.fromGraph(GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val loadImage = builder.add(Flow[ByteString].map { bytes ⇒
        ImageIOResizer.loadImage(bytes.toArray)
      })

      val resizeImage = builder.add(Flow[BufferedImage].map { originalImage ⇒
        val image = ImageIOResizer.resize(originalImage, thumbnailCreatorConfig.size)
        val outputStream = ByteStringOutputStream()
        ImageIOResizer.compress(image, outputStream, thumbnailCreatorConfig.format, thumbnailCreatorConfig.quality)
        outputStream.toByteString
      })

      val createMetadata = builder.add(Flow[ByteString].map { data ⇒
        Metadata(Some(Metadata.Tag("imageio", "thumbnail", Metadata.Tag.Disposition.CONTENT)),
          Metadata.Value.Preview(Metadata.Preview(thumbnailCreatorConfig.format, data)))
      })

      val concatBytes = Flow[ByteString].fold(ByteString.empty)(_ ++ _)
      val concatInput = builder.add(concatBytes)
      val concatOutput = builder.add(concatBytes)

      concatInput ~> loadImage ~> resizeImage ~> concatOutput ~> createMetadata
      FlowShape(concatInput.in, createMetadata.out)
    })
    flow.addAttributes(Attributes.name("imageioThumbnailCreator"))
  }
}
