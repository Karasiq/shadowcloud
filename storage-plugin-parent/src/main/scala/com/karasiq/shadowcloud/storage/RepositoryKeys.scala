package com.karasiq.shadowcloud.storage

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.wrappers.{LongSeqRepositoryWrapper, PrefixedRepositoryWrapper}
import com.karasiq.shadowcloud.utils.HexString

import scala.language.postfixOps

object RepositoryKeys {
  def toHexString(underlying: BaseRepository): Repository[ByteString] = {
    Repository.mapKeys(underlying, HexString.decode, HexString.encode)
  }

  def toLong(underlying: BaseRepository): SeqRepository[Long] = {
    new LongSeqRepositoryWrapper(underlying)
  }

  def withPrefix(underlying: BaseRepository, delimiter: String = "_"): CategorizedRepository[String, String] = {
    new PrefixedRepositoryWrapper(underlying, delimiter)
  }
}
