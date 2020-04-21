package com.karasiq.shadowcloud.test.index



import com.karasiq.shadowcloud.model.{Folder, Timestamp}
import com.karasiq.shadowcloud.test.utils.{CoreTestUtils, TestUtils}
import org.scalatest.{FlatSpec, Matchers}

class FolderTest extends FlatSpec with Matchers {
  val folder = Folder(TestUtils.randomString, Timestamp(System.currentTimeMillis() - 1, System.currentTimeMillis() - 1))

  "Folder" should "add file" in {
    val testFile = CoreTestUtils.randomFile(folder.path)
    val folder1 = folder.addFiles(testFile)
    folder1.timestamp.created shouldBe folder.timestamp.created
    folder1.timestamp.lastModified should be > folder.timestamp.lastModified
    folder1.files shouldBe Set(testFile)
  }

  ignore should "throw exception on invalid path" in {
    val testFile = CoreTestUtils.randomFile(folder.path / "test")
    intercept[AssertionError](folder.addFiles(testFile))
  }

  it should "remove file" in {
    val testFile = CoreTestUtils.randomFile(folder.path)
    val folder1 = folder.addFiles(testFile)
    val folder2 = folder.deleteFiles(folder1.files.head)
    folder2.timestamp.lastModified should be > folder.timestamp.lastModified
    folder2.timestamp.lastModified should be >= folder1.timestamp.lastModified
    folder2.files shouldBe empty
  }

  it should "add subdirectory" in {
    val subDir = TestUtils.randomString
    val folder1 = folder.addFolders(subDir)
    folder1.timestamp.lastModified should be > folder.timestamp.lastModified
    folder1.folders shouldBe Set(subDir)
  }

  it should "remove subdirectory" in {
    val subDir = TestUtils.randomString
    val folder1 = folder.addFolders(subDir)
    val folder2 = folder1.deleteFolders(subDir)
    folder2.timestamp.lastModified should be > folder.timestamp.lastModified
    folder2.timestamp.lastModified should be >= folder1.timestamp.lastModified
    folder2.folders shouldBe empty
  }

  it should "change path" in {
    val newPath = folder.path / TestUtils.randomString
    val folder1 = folder.withPath(newPath)
    folder1.path shouldBe newPath
    folder1.files shouldBe folder.files.map(file ⇒ file.copy(path = file.path.withParent(newPath)))
  }
}
