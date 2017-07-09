package com.karasiq.shadowcloud.metadata.imageio.utils

import javax.imageio.{IIOImage, ImageIO, ImageWriteParam, ImageWriter}
import java.awt.{Dimension, Image, Toolkit}
import java.awt.image._
import java.awt.RenderingHints._
import java.io.OutputStream

import scala.collection.JavaConverters._

object ImageIOResizer {
  private[this] object renderingHints {
    val fast = Map(
      KEY_RENDERING → VALUE_RENDER_SPEED,
      KEY_COLOR_RENDERING → VALUE_COLOR_RENDER_SPEED,
      KEY_ANTIALIASING → VALUE_ANTIALIAS_OFF,
      KEY_ALPHA_INTERPOLATION → VALUE_ALPHA_INTERPOLATION_SPEED,
      KEY_INTERPOLATION → VALUE_INTERPOLATION_NEAREST_NEIGHBOR
    ).asJava

    val default = Map(
      KEY_RENDERING → VALUE_RENDER_SPEED,
      KEY_COLOR_RENDERING → VALUE_COLOR_RENDER_SPEED,
      KEY_ANTIALIASING → VALUE_ANTIALIAS_ON,
      KEY_ALPHA_INTERPOLATION → VALUE_ALPHA_INTERPOLATION_SPEED,
      KEY_INTERPOLATION → VALUE_INTERPOLATION_BILINEAR
    ).asJava

    val highQuality = Map(
      KEY_RENDERING → VALUE_RENDER_QUALITY,
      KEY_COLOR_RENDERING → VALUE_COLOR_RENDER_QUALITY,
      KEY_ANTIALIASING → VALUE_ANTIALIAS_ON,
      KEY_ALPHA_INTERPOLATION → VALUE_ALPHA_INTERPOLATION_QUALITY,
      KEY_INTERPOLATION → VALUE_INTERPOLATION_BICUBIC
    ).asJava
  }

  private[this] def getScaledDimension(imgSize: Dimension, boundary: Dimension): Dimension = {
    var newWidth: Int = imgSize.width
    var newHeight: Int = imgSize.height
    if (imgSize.width > boundary.width) {
      newWidth = boundary.width
      newHeight = (newWidth * imgSize.height) / imgSize.width
    }
    if (newHeight > boundary.height) {
      newHeight = boundary.height
      newWidth = (newHeight * imgSize.width) / imgSize.height
    }
    new Dimension(newWidth, newHeight)
  }

  private[this] def redrawImage(image: Image, size: Dimension): BufferedImage = {
    val outputImage = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB)
    val graphics = outputImage.createGraphics()
    try {
      // Max quality settings
      graphics.setRenderingHints(renderingHints.default)
      graphics.drawImage(image, 0, 0, size.width, size.height, null)
      outputImage
    } finally {
      graphics.dispose()
    }
  }

  // Fixes JPG colors
  def loadImage(bytes: Array[Byte]): BufferedImage = {
    val image = Toolkit.getDefaultToolkit.createImage(bytes)

    val rgbMasks: Array[Int] = Array(0xFF0000, 0xFF00, 0xFF)
    val rgbOpaqueModel: ColorModel = new DirectColorModel(32, rgbMasks(0), rgbMasks(1), rgbMasks(2))

    val pg = new PixelGrabber(image, 0, 0, -1, -1, true)
    pg.grabPixels()

    (pg.getWidth, pg.getHeight, pg.getPixels) match {
      case (width, height, pixels: Array[Int]) ⇒
        val buffer = new DataBufferInt(pixels, width * height)
        val raster = Raster.createPackedRaster(buffer, width, height, width, rgbMasks, null)
        new BufferedImage(rgbOpaqueModel, raster, false, null)

      case _ ⇒
        throw new IllegalArgumentException(s"Not supported: $pg")
    }
  }

  def compress(image: BufferedImage, outputStream: OutputStream, format: String = "jpeg", quality: Int = 80): Unit = {
    val imageOutputStream = ImageIO.createImageOutputStream(outputStream)
    try {
      val writer: ImageWriter = ImageIO.getImageWritersByFormatName(format).next
      val iwp: ImageWriteParam = writer.getDefaultWriteParam
      if (iwp.canWriteCompressed) {
        iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
        iwp.setCompressionQuality(quality.toFloat / 100.0f)
      }
      writer.setOutput(imageOutputStream)
      writer.write(null, new IIOImage(image, null, null), iwp)
      writer.dispose()
      imageOutputStream.flush()
    } finally {
      imageOutputStream.close()
    }
  }

  def resize(image: BufferedImage, size: Int): BufferedImage = {
    val imgSize = new Dimension(image.getWidth, image.getHeight)
    val boundary = new Dimension(size, size)
    val newSize = getScaledDimension(imgSize, boundary)
    redrawImage(image, newSize)
  }
}
