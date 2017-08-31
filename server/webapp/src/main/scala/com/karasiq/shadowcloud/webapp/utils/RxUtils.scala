package com.karasiq.shadowcloud.webapp.utils

import rx.Rx

import com.karasiq.shadowcloud.model.{Folder, Path, RegionId}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

private[webapp] object RxUtils {
  import com.karasiq.bootstrap.Bootstrap.default.scalaRxContext
  import AppContext.jsExecutionContext

  def toFolderRx(regionId: RegionId, pathRx: Rx[Path])(implicit ac: AppContext): RxWithKey[Path, Folder] = {
    val folderRx = RxWithKey(pathRx.now, Folder.create(pathRx.now))(ac.api.getFolder(regionId, _))
    pathRx.triggerLater(folderRx.update(pathRx.now))
    folderRx
  }

  def toSelectedFolderRx(fc: FolderContext)(implicit ac: AppContext): RxWithKey[Path, Folder] = {
    toFolderRx(fc.regionId, fc.selected)
  }
}
