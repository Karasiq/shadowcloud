package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.storage.Repository.BaseRepository

import scala.language.postfixOps

private[storage] final class MultiSeqRepositoryWrapper[SeqKey, @specialized(Long) SubKey](underlying: BaseRepository,
  mapKey: String ⇒ SeqKey, mapSubKey: String ⇒ SubKey, delimiter: String)
  extends CategorizedRepository[SeqKey, SubKey] with SeqRepository[(SeqKey, SubKey)] {

  def keys: Source[(SeqKey, SubKey), Result] = {
    underlying.keys.map(fromString)
  }

  def read(key: (SeqKey, SubKey)): Source[Data, Result] = {
    underlying.read(toString(key))
  }

  def write(key: (SeqKey, SubKey)): Sink[Data, Result] = {
    underlying.write(toString(key))
  }

  private[this] def fromString(key: String): (SeqKey, SubKey) = {
    val Array(seqKey, subKey) = key.split(delimiter, 2)
    (mapKey(seqKey), mapSubKey(subKey))
  }

  private[this] def toString(key: (SeqKey, SubKey)): String = {
    s"${key._1}$delimiter${key._2}"
  }
}
