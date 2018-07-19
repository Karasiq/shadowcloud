package com.karasiq.shadowcloud.webapp.locales

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.locales.impl.Locales

trait AppLocale {
  def languageCode: String

  def name: String
  def path: String
  def value: String
  def config: String
  def size: String
  def empty: String
  def show: String
  def edit: String
  def submit: String
  def cancel: String
  def close: String
  def unknown: String
  def total: String
  def entries(value: Long): String

  def file: String
  def folder: String

  def delete: String
  def move: String
  def copy: String
  def rename: String

  def rootPath: String
  def emptyPath: String
  def createFolder: String
  def deleteFolder: String

  def video: String
  def image: String
  def audio: String

  def uploadFiles: String
  def downloadFile: String
  def deleteFile: String
  def repairFile: String
  def playFile: String
  def viewTextFile: String
  def inspectFile: String
  def pasteText: String

  def fileId: String
  def createdDate: String
  def modifiedDate: String
  def revision: String

  def preview: String
  def metadata: String
  def content: String

  def availability: String
  def revisions: String

  def foldersView: String
  def regionsView: String

  def storage: String
  def storages: String
  def region: String
  def regions: String
  def indexScope: String
  def currentScope: String
  def historyScope: String
  def indexSnapshotDate: String

  def collectGarbage: String
  def compactIndex: String
  def repairRegion: String

  def writableSpace: String
  def totalSpace: String
  def freeSpace: String
  def usedSpace: String

  def regionId: String
  def regionIdHint: String
  def uniqueRegionId: String
  def storageId: String
  def storageType: String
  def createRegion: String
  def createStorage: String
  def registerStorage: String
  def registerRegion: String
  def unregisterStorage: String
  def unregisterRegion: String
  def suspend: String
  def resume: String

  def keys: String
  def keyId: String
  def keyEncAlgorithm: String
  def keySignAlgorithm: String
  def keyPermissions: String
  def keyForEncryption: String
  def keyForDecryption: String
  def generateKey: String
  def exportKey: String
  def importKey: String

  def metadataParser: String
  def metadataDisposition: String
  def metadataType: String
  def metadataFormat: String

  def metadataText: String
  def metadataTable: String
  def metadataFileList: String
  def metadataThumbnail: String
  def metadataImageData: String
  def metadataEmbeddedResources: String

  def deleteFolderConfirmation(path: Path): String
}

object AppLocale {
  def forCode(code: String): AppLocale = {
    val langCode = code.toLowerCase.split("-", 2).headOption
    Locales.all.find(lc ⇒ langCode.contains(lc.languageCode)).getOrElse(Locales.default)
  }
}                               