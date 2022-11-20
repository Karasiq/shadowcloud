package com.karasiq.shadowcloud.webapp.utils

import rx.{Rx, Var}

import com.karasiq.shadowcloud.model.{File, Folder, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

private[webapp] object RxUtils {
  import com.karasiq.bootstrap.Bootstrap.default.scalaRxContext
  import AppContext.JsExecutionContext

  def toFolderRx(path: Path)(implicit fc: FolderContext, ac: AppContext): Rx[Folder] = {
    createContextFolderRx(fc.regionId, Var(path), fc.scope).toRx
  }

  def toFilesRx(path: Path)(implicit fc: FolderContext, ac: AppContext): Rx[Set[File]] = {
    createContextFilesRx(fc.regionId, Var(path), fc.scope).toRx
  }

  def getSelectedFolderRx(implicit fc: FolderContext, ac: AppContext): Rx[Folder] = {
    createContextFolderRx(fc.regionId, fc.selected, fc.scope).toRx
  }

  def getDownloadLinkRx(file: File, useId: Boolean = false)(implicit ac: AppContext, fc: FolderContext): Rx[String] = {
    Rx {
      if (useId)
        ac.api.fileUrl(fc.regionId, file.path, file.id, fc.scope())
      else
        ac.api.mostRecentFileUrl(fc.regionId, file.path, fc.scope())
    }
  }

  private[this] def createFolderRx(regionId: RegionId, pathRx: Rx[Path], scopeRx: Rx[IndexScope])(implicit
      ac: AppContext
  ): RxWithKey[(Path, IndexScope), Folder] = {
    val folderRx = RxWithKey(Rx((pathRx(), scopeRx())), Folder.create(pathRx.now)) { case (path, scope) ⇒
      ac.api.getFolder(regionId, path, dropChunks = true, scope)
    }

    folderRx
  }

  private[this] def createContextFolderRx(regionId: RegionId, pathRx: Rx[Path], scopeRx: Rx[IndexScope])(implicit
      fc: FolderContext,
      ac: AppContext
  ): RxWithKey[(Path, IndexScope), Folder] = {
    val folderRx = createFolderRx(regionId, pathRx, scopeRx)
    fc.updates.filter(_._1 == pathRx.now).triggerLater(folderRx.update()) // Subscribe to updates
    folderRx
  }

  private[this] def createContextFilesRx(regionId: RegionId, pathRx: Rx[Path], scopeRx: Rx[IndexScope])(implicit
      fc: FolderContext,
      ac: AppContext
  ): RxWithKey[(Path, IndexScope), Set[File]] = {
    val filesRx = RxWithKey(Rx(pathRx(), scopeRx()), Set.empty[File]) { case (path, scope) ⇒
      ac.api.getFiles(regionId, path, dropChunks = true, scope = scope)
    }
    fc.updates.filter(_._1 == pathRx.now.parent).triggerLater(filesRx.update())
    filesRx
  }
}
