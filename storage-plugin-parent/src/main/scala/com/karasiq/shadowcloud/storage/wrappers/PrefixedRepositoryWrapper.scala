package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.storage.{BaseRepository, CategorizedRepository}

import scala.language.postfixOps

private[storage] final class PrefixedRepositoryWrapper(underlying: BaseRepository, delimiter: String)
  extends CategorizedRepository[String, String] {

  def keys: Source[(String, String), Result] = {
    underlying.keys.map(fromString)
  }

  def read(key: (String, String)): Source[Data, Result] = {
    underlying.read(toString(key))
  }

  def write(key: (String, String)): Sink[Data, Result] = {
    underlying.write(toString(key))
  }

  private[this] def fromString(key: String): (String, String) = {
    val Array(seqKey, subKey) = key.split(delimiter, 2)
    (seqKey, subKey)
  }

  private[this] def toString(key: (String, String)): String = {
    s"${key._1}$delimiter${key._2}"
  }
}
