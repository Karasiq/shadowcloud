package com.karasiq.shadowcloud.test.utils

import akka.util.ByteString
import org.scalatest.FlatSpec

import com.karasiq.shadowcloud.utils.ByteStringUnsafe

class ByteStringUnsafeTest extends FlatSpec {
  "ByteString array" should "be extracted" in {
    val array          = new Array[Byte](4)
    val bs             = ByteString.fromArrayUnsafe(array)
    val extractedArray = ByteStringUnsafe.getArray(bs)
    assert(extractedArray eq array, "Arrays not equal")
  }
}
