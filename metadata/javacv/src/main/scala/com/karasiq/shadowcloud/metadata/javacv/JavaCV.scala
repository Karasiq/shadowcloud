package com.karasiq.shadowcloud.metadata.javacv

import org.bytedeco.javacpp.opencv_core._
import org.bytedeco.javacpp.opencv_imgcodecs._

/** JavaCV helper object
  */
private[javacv] object JavaCV {

  /** Creates JPEG-encoded image
    * @param image JavaCV image
    * @param quality JPEG quality
    * @return Bytes of JPEG-encoded image
    */
  def asJpeg(image: IplImage, quality: Int = 85): Array[Byte] = {
    val matrix = cvEncodeImage(".jpeg", image.asCvMat(), Array(CV_IMWRITE_JPEG_QUALITY, quality, 0))
    try {
      val dataPtr  = matrix.data_ptr()
      val dataSize = matrix.size()
      val outArray = new Array[Byte](dataSize)
      dataPtr.get(outArray, 0, dataSize)
      outArray
    } finally matrix.release()
  }
}
