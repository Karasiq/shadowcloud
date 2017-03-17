package com.karasiq.shadowcloud.storage.wrappers

import com.karasiq.shadowcloud.storage.Repository

import scala.language.postfixOps

private[storage] class RepositoryWrapper[Key](underlying: Repository[Key])
  extends RepositoryKeyMapper[Key, Key](underlying, identity, identity) {

  override def toString: String = {
    underlying.toString
  }
}