package com.karasiq.shadowcloud.storage.wrappers

import com.karasiq.shadowcloud.storage.Repository.BaseRepository

import scala.language.postfixOps

private[shadowcloud] object RepositoryWrappers {
  def hexString(underlying: BaseRepository): ByteStringRepository = {
    new HexStringRepositoryWrapper(underlying)
  }

  def longSeq(underlying: BaseRepository): LongSeqRepository = {
    new LongSeqRepositoryWrapper(underlying)
  }
}
