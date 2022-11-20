package akka.util

import xerial.larray.{LArray, LByteArray}

object BSLArray {
  def toLArray(arr: Array[Byte]): LByteArray = {
    val lb = new LByteArray(arr.length)
    for (i ← arr.indices) lb(i) = arr(i)
    lb
  }

  def toHeapArray(lb: LArray[Byte]): Array[Byte] = {
    val heapArr = new Array[Byte](lb.length.ensuring(_.isValidInt).toInt)
    for (i ← heapArr.indices) heapArr(i) = lb(i)
    heapArr
  }
}
