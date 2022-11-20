package com.karasiq.shadowcloud.storage.repository

import akka.util.ByteString
import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.storage.repository.wrappers.LongSeqRepositoryWrapper

object RepositoryKeys {
  def toHexString(repository: KeyValueRepository): Repository[ByteString] = {
    Repository.mapKeys(repository, HexString.decode, HexString.encode)
  }

  def toLong(repository: KeyValueRepository): SeqRepository[Long] = {
    // Repository.toSeq(Repository.mapKeys[String, Long](underlying, _.toLong, _.toString))
    new LongSeqRepositoryWrapper(repository)
  }
}
