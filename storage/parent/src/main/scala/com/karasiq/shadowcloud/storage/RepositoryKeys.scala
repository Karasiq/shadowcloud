package com.karasiq.shadowcloud.storage

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.wrappers.{LongSeqRepositoryWrapper, PrefixedRepositoryWrapper}
import com.karasiq.shadowcloud.utils.HexString

import scala.language.postfixOps

object RepositoryKeys {
  def toHexString(repository: BaseRepository): Repository[ByteString] = {
    Repository.mapKeys(repository, HexString.decode, HexString.encode)
  }

  def toLong(repository: BaseRepository): SeqRepository[Long] = {
    // Repository.toSeq(Repository.mapKeys[String, Long](underlying, _.toLong, _.toString))
    new LongSeqRepositoryWrapper(repository)
  }

  def withPrefix(repository: BaseRepository, delimiter: String = "_"): CategorizedRepository[String, String] = {
    new PrefixedRepositoryWrapper(repository, delimiter)
  }
}
