package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.Source
import com.karasiq.shadowcloud.storage.Repository

import scala.collection.immutable.TreeSet
import scala.language.postfixOps

trait SeqRepository[@specialized(Long) Key] extends Repository[Key] {
  def sortedKeys(implicit ord: Ordering[Key]): Source[Key, _] = {
    keys.fold(TreeSet.empty[Key])(_ + _).mapConcat(identity)
  }

  def keysBefore(id: Key)(implicit ord: Ordering[Key]): Source[Key, _] = {
    keys.filter(ord.lt(_, id))
  }

  def keysAfter(id: Key)(implicit ord: Ordering[Key]): Source[Key, _] = {
    keys.filter(ord.gt(_, id))
  }
}

trait LongSeqRepository extends SeqRepository[Long]