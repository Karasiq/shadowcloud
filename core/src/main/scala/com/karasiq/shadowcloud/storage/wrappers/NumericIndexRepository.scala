package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.Source
import com.karasiq.shadowcloud.storage.IndexRepository

import scala.collection.immutable.TreeSet
import scala.language.postfixOps

trait NumericIndexRepository extends IndexRepository[Long] {
  def sortedKeys(implicit ord: Ordering[Long]): Source[Long, _] = {
    keys.fold(TreeSet.empty[Long])(_ + _).mapConcat(identity)
  }

  def keysBefore(id: Long): Source[Long, _] = {
    keys.filter(_ < id)
  }

  def keysAfter(id: Long): Source[Long, _] = {
    keys.filter(_ > id)
  }
}
