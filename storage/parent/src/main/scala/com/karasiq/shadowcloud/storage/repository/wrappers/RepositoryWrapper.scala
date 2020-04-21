package com.karasiq.shadowcloud.storage.repository.wrappers



import com.karasiq.shadowcloud.storage.repository.Repository

private[repository] class RepositoryWrapper[Key](underlying: Repository[Key])
  extends RepositoryKeyMapper[Key, Key](underlying, identity, identity) {

  override def toString: String = {
    underlying.toString
  }
}
