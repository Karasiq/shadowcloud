package com.shadowcloud.api.test

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.api.boopickle.SCBooPickleEncoding
import com.karasiq.shadowcloud.api.json.SCJsonEncoding
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.{File, Folder}

class ApiEncodingTest extends FlatSpec with Matchers {
  val testDiff = IndexDiff.newFolders(Folder("/", folders = Set("123"), files = Set(File("/321.txt"))))

  "BooPickle" should "encode diff" in {
    val enc = SCBooPickleEncoding
    import enc.implicits._
    val bytes   = enc.encode(testDiff)
    val decoded = enc.decode[IndexDiff](bytes)
    decoded shouldBe testDiff
  }

  "JSON" should "encode diff" in {
    val enc = SCJsonEncoding
    import enc.implicits._
    val bytes   = enc.encode(testDiff)
    val decoded = enc.decode[IndexDiff](bytes)
    decoded shouldBe testDiff
  }
}
