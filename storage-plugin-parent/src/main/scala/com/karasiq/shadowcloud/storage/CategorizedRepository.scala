package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.Source
import com.karasiq.shadowcloud.storage.wrappers.RepositoryKeyMapper

import scala.language.postfixOps

trait CategorizedRepository[CatKey, ItemKey] extends Repository[(CatKey, ItemKey)] {
  def subRepository(seq: CatKey): Repository[ItemKey] = {
    new RepositoryKeyMapper[(CatKey, ItemKey), ItemKey](this, _._2, (seq, _)) {
      override def keys: Source[ItemKey, Result] = {
        CategorizedRepository.this.keys
          .filter(_._1 == seq)
          .map(_._2)
      }
    }
  }
}
