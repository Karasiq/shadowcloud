package com.karasiq.shadowcloud.webapp.context

import rx.Var

import com.karasiq.shadowcloud.model.{Path, RegionId}

trait FolderContext {
  def regionId: RegionId
  def selected: Var[Path]
}

object FolderContext {
  def apply(_regionId: RegionId): FolderContext = {
    new FolderContext {
      val regionId = _regionId
      val selected = Var(Path.root)
    }
  }
}
