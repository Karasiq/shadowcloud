package com.karasiq.shadowcloud.storage.repository

import scala.language.postfixOps

import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.storage.repository.wrappers.{PrefixedRepositoryWrapper, RepositoryKeyMapper}

trait CategorizedRepository[CatKey, ItemKey] extends Repository[(CatKey, ItemKey)] {
  def subKeys(seq: CatKey): Source[ItemKey, Result] = {
    this.keys
      .filter(_._1 == seq)
      .map(_._2)
  }

  def subRepository(seq: CatKey): Repository[ItemKey] = {
    val outerInstance = this
    new RepositoryKeyMapper[(CatKey, ItemKey), ItemKey](outerInstance, _._2, (seq, _)) {
      override def keys: Source[ItemKey, Result] = outerInstance.subKeys(seq)
      override def toString: String = s"SubRepository($seq in $outerInstance)"
    }
  }
}

object CategorizedRepository {
  def fromKeyValue(repository: KeyValueRepository, delimiter: String = "_"): CategorizedRepository[String, String] = {
    new PrefixedRepositoryWrapper(repository, delimiter)
  }
}
