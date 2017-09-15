package com.karasiq.shadowcloud.test.index

import org.scalatest.{FlatSpec, Matchers}

import com.karasiq.shadowcloud.index.FolderIndex
import com.karasiq.shadowcloud.model.Folder

class FolderIndexTest extends FlatSpec with Matchers {
  "Folder index" should "traverse folder tree" in {
    val folder1 = Folder.create("/f1").addFolders("f2")
    val folder2 = Folder.create("/f1/f2").addFolders("f3", "f33")
    val folder3 = Folder.create("/f1/f2/f3").addFolders("f4")
    val folder4 = Folder.create("/f1/f2/f33")
    val folder5 = Folder.create("/f1/f2/f3/f4")
    val folder6 = Folder.create("/f2/f1")
    val index = FolderIndex(Seq(folder1, folder2, folder3, folder4, folder5, folder6))
    FolderIndex.traverseFolderTree(index, "/f1").toList shouldBe Seq(folder1, folder2, folder3, folder5, folder4)
  }
}
