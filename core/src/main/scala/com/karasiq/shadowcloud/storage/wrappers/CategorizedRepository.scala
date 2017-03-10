package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.storage.Repository

import scala.language.postfixOps

trait CategorizedRepository[CatKey, ItemKey] extends Repository[(CatKey, ItemKey)] {
  def subRepository(seq: CatKey): Repository[ItemKey] = new Repository[ItemKey] {
    def keys: Source[ItemKey, Result] = {
      CategorizedRepository.this.keys
        .filter(_._1 == seq)
        .map(_._2)
    }

    def read(key: ItemKey): Source[Data, Result] = {
      CategorizedRepository.this.read((seq, key))
    }

    def write(key: ItemKey): Sink[Data, Result] = {
      CategorizedRepository.this.write((seq, key))
    }
  }
}
