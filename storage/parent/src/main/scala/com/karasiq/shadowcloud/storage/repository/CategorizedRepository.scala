package com.karasiq.shadowcloud.storage.repository

import akka.stream.scaladsl.Source
import com.karasiq.shadowcloud.storage.repository.wrappers.{PrefixedRepositoryWrapper, RepositoryKeyMapper}

trait CategorizedRepository[CatKey, ItemKey] extends Repository[(CatKey, ItemKey)] {
  def subKeys(seq: CatKey): Source[ItemKey, Result] = {
    this.keys
      .filter(_._1 == seq)
      .map(_._2)
  }

  def subRepository(seq: CatKey): Repository[ItemKey] = {
    new RepositoryKeyMapper[(CatKey, ItemKey), ItemKey](this, _._2, (seq, _)) {
      override def keys: Source[ItemKey, Result] = CategorizedRepository.this.subKeys(seq)
      override def toString: String              = s"SubRepository($seq in ${CategorizedRepository.this})"
    }
  }
}

object CategorizedRepository {
  def fromKeyValue(repository: KeyValueRepository, delimiter: String = "_"): CategorizedRepository[String, String] = {
    new PrefixedRepositoryWrapper(repository, delimiter)
  }
}
