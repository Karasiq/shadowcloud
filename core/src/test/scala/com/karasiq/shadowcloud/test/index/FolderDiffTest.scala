package com.karasiq.shadowcloud.test.index

import scala.language.postfixOps

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.index.{Folder, FolderIndex, Path}
import com.karasiq.shadowcloud.index.diffs.FolderIndexDiff
import com.karasiq.shadowcloud.test.utils.TestUtils

class FolderDiffTest extends FlatSpec with Matchers {
  val folder1 = TestUtils.randomFolder()
  val newFile = TestUtils.randomFile(folder1.path)
  val newFolder = TestUtils.randomString
  val folder2 = folder1.addFiles(newFile).addFolders(newFolder)

  "Folder diff" should "find new files" in {
    val diff = folder2.diff(folder1)
    diff.path shouldBe folder1.path
    diff.deletedFiles shouldBe empty
    diff.deletedFolders shouldBe empty
    diff.newFolders shouldBe Set(newFolder)
    diff.newFiles shouldBe Set(newFile)
  }

  it should "find deleted files" in {
    val folder3 = TestUtils.randomFolder(folder1.path)
    val diff = folder3.diff(folder2)
    diff.newFiles shouldBe folder3.files
    diff.newFolders shouldBe folder3.folders
    diff.deletedFiles shouldBe folder2.files
    diff.deletedFolders shouldBe folder2.folders
  }

  it should "modify folder" in {
    val diff = folder2.diff(folder1)
    val folder3 = folder1.patch(diff)
    folder3 shouldBe folder2
  }

  it should "modify folder index" in {
    val index = FolderIndex(Seq(folder1))
    val diff = folder2.diff(folder1)
    val index1 = index.patch(FolderIndexDiff.seq(diff))
    index1.folders shouldBe Map(
      Path.root → Folder(Path.root, folder1.timestamp, Set(folder1.path.name)),
      folder1.path → folder2
    ).++(folder2.folders.map(name ⇒ (folder2.path / name) → Folder(folder2.path / name, folder2.timestamp)) ++
      folder1.folders.map(name ⇒ (folder1.path / name) → Folder(folder1.path / name, folder1.timestamp)))
  }

  it should "add folder with parents" in {
    val index = FolderIndex.empty.patch(FolderIndexDiff.create(Folder.create("/test1/test2/test3/test4")))
    index.folders("/").folders should contain ("test1")
    index.folders("/test1").folders should contain ("test2")
    index.folders("/test1/test2").folders should contain ("test3")
    index.folders("/test1/test2/test3").folders should contain ("test4")
  }

  it should "delete folder with children" in {
    val index = FolderIndex.empty
      .patch(FolderIndexDiff.create(Folder.create("/test1/test2/test3/test4")))
      .patch(FolderIndexDiff.deleteFolderPaths("/test1"))
    index.folders("/").folders shouldBe empty
    intercept[NoSuchElementException](index.folders("/test1"))
    intercept[NoSuchElementException](index.folders("/test1/test2"))
    intercept[NoSuchElementException](index.folders("/test1/test2/test3"))
    intercept[NoSuchElementException](index.folders("/test1/test2/test3/test4"))
  }

  it should "reverse" in {
    val diff = folder2.diff(folder1)
    val folder3 = folder1.patch(diff)
    val reverse = diff.reverse
    reverse.time shouldBe diff.time
    reverse.newFiles shouldBe empty
    reverse.newFolders shouldBe empty
    reverse.deletedFiles shouldBe Set(newFile)
    reverse.deletedFolders shouldBe Set(newFolder)
    val folder4 = folder3.patch(reverse)
    folder4 shouldBe folder1.copy(timestamp = folder1.timestamp.modified(diff.time))
  }

  it should "merge" in {
    val folder3 = TestUtils.randomFolder(folder1.path)
    val diff = folder2.diff(folder1) // + Folder2 files
    val diff1 = folder3.diff(folder2) // - Folder1 files, - Folder2 files, + Folder3 files
    val merged = diff.merge(diff1) // - Folder1 files, + Folder3 files
    merged.newFiles shouldBe folder3.files
    merged.newFolders shouldBe folder3.folders
    merged.deletedFiles shouldBe folder1.files
    merged.deletedFolders shouldBe folder1.folders
  }

  it should "diff" in {
    val folder3 = TestUtils.randomFolder(folder1.path)
    val diff = folder2.diff(folder1) // + Folder2 files
    val diff1 = folder3.diff(folder2) // - Folder1 files, - Folder2 files, + Folder3 files
    val diffDiff = diff.diff(diff1) // + Folder1 files, + Folder2 files, - Folder3 files
    diffDiff.newFiles shouldBe (folder1.files ++ folder2.files -- folder3.files)
    diffDiff.newFolders shouldBe (folder1.folders ++ folder2.folders -- folder3.folders)
    diffDiff.deletedFiles shouldBe folder3.files
    diffDiff.deletedFolders shouldBe folder3.folders
  }

  it should "extract deletes and creates" in {
    val diff = TestUtils.randomFolder(folder1.path).diff(folder1)
    diff.newFiles should not be empty
    diff.newFolders should not be empty
    diff.deletedFiles should not be empty
    diff.deletedFolders should not be empty
    val deletes = diff.deletes
    deletes.deletedFiles shouldBe diff.deletedFiles
    deletes.deletedFolders shouldBe diff.deletedFolders
    deletes.newFiles shouldBe empty
    deletes.newFolders shouldBe empty
    val creates = diff.creates
    creates.newFiles shouldBe diff.newFiles
    creates.newFolders shouldBe diff.newFolders
    creates.deletedFiles shouldBe empty
    creates.deletedFolders shouldBe empty
  }
}
