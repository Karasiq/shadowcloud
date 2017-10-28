package com.karasiq.shadowcloud.metadata.javacv

import java.nio.file.Path

import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgcodecs._

/**
  * JavaCV helper object
  */
private[javacv] object JavaCV {
  def withImage[T](image: IplImage)(f: IplImage ⇒ T): T = {
    try f(image) finally cvReleaseImage(image)
  }

  def withImageFile[T](path: Path)(f: IplImage ⇒ T): T = {
    val image = cvLoadImage(path.toAbsolutePath.toString)
    try f(image) finally cvReleaseImage(image)
  }

  /**
    * Creates JPEG-encoded image
    * @param image JavaCV image
    * @param quality JPEG quality
    * @return Bytes of JPEG-encoded image
    */
  def asJpeg(image: IplImage, quality: Int = 85): Array[Byte] = {
    val matrix = cvEncodeImage(".jpeg", image.asCvMat(), Array(CV_IMWRITE_JPEG_QUALITY, quality, 0))
    try {
      val dataPtr = matrix.data_ptr()
      val dataSize = matrix.size()
      val outArray = new Array[Byte](dataSize)
      dataPtr.get(outArray, 0, dataSize)
      outArray
    } finally cvReleaseMat(matrix)
  }
}