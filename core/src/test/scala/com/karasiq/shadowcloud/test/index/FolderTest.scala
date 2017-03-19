package com.karasiq.shadowcloud.test.index

import com.karasiq.shadowcloud.index.Folder
import com.karasiq.shadowcloud.test.utils.TestUtils
import org.scalatest.{FlatSpec, Matchers}

import scala.language.postfixOps

class FolderTest extends FlatSpec with Matchers {
  val folder = Folder(TestUtils.randomString, System.currentTimeMillis() - 1, System.currentTimeMillis() - 1)

  "Folder" should "add file" in {
    val testFile = TestUtils.randomFile(folder.path)
    val folder1 = folder.addFiles(testFile)
    folder1.created shouldBe folder.created
    folder1.lastModified should be > folder.lastModified
    folder1.files shouldBe Set(testFile)
  }

  it should "throw exception on invalid path" in {
    val testFile = TestUtils.randomFile(folder.path / "test")
    intercept[IllegalArgumentException](folder.addFiles(testFile))
  }

  it should "remove file" in {
    val testFile = TestUtils.randomFile(folder.path)
    val folder1 = folder.addFiles(testFile)
    val folder2 = folder.deleteFiles(folder1.files.head)
    folder2.lastModified should be > folder.lastModified
    folder2.lastModified should be >= folder1.lastModified
    folder2.files shouldBe empty
  }

  it should "add subdirectory" in {
    val subDir = TestUtils.randomString
    val folder1 = folder.addFolders(subDir)
    folder1.lastModified should be > folder.lastModified
    folder1.folders shouldBe Set(subDir)
  }

  it should "remove subdirectory" in {
    val subDir = TestUtils.randomString
    val folder1 = folder.addFolders(subDir)
    val folder2 = folder1.deleteFolders(subDir)
    folder2.lastModified should be > folder.lastModified
    folder2.lastModified should be >= folder1.lastModified
    folder2.folders shouldBe empty
  }

  it should "change path" in {
    val newPath = folder.path / TestUtils.randomString
    val folder1 = folder.withPath(newPath)
    folder1.path shouldBe newPath
    folder1.files shouldBe folder.files.map(file ⇒ file.copy(path = file.path.withParent(newPath)))
  }
}
