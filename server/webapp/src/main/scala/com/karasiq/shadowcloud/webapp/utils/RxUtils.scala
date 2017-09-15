package com.karasiq.shadowcloud.webapp.utils

import rx.{Rx, Var}

import com.karasiq.shadowcloud.model.{Folder, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

private[webapp] object RxUtils {
  import com.karasiq.bootstrap.Bootstrap.default.scalaRxContext
  import AppContext.JsExecutionContext

  def toFolderRx(path: Path)(implicit fc: FolderContext, ac: AppContext): Rx[Folder] = {
    createContextFolderRx(fc.regionId, Var(path), fc.scope).toRx
  }

  def getSelectedFolderRx(implicit fc: FolderContext, ac: AppContext): Rx[Folder] = {
    createContextFolderRx(fc.regionId, fc.selected, fc.scope).toRx
  }

  private[this] def createFolderRx(regionId: RegionId, pathRx: Rx[Path], scopeRx: Rx[IndexScope])
                              (implicit ac: AppContext): RxWithKey[(Path, IndexScope), Folder] = {
    val folderRx = RxWithKey(Rx((pathRx(), scopeRx())), Folder.create(pathRx.now)) { case (path, scope) ⇒
      ac.api.getFolder(regionId, path, dropChunks = true, scope)
    }

    folderRx
  }

  private[this] def createContextFolderRx(regionId: RegionId, pathRx: Rx[Path], scopeRx: Rx[IndexScope])
                                     (implicit fc: FolderContext, ac: AppContext): RxWithKey[(Path, IndexScope), Folder] = {
    val folderRx = createFolderRx(regionId, pathRx, scopeRx)
    fc.updates.filter(_._1 == pathRx.now).triggerLater(folderRx.update()) // Subscribe to updates
    folderRx
  }
}
