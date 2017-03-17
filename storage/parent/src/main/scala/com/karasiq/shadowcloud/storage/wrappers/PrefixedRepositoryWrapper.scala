package com.karasiq.shadowcloud.storage.wrappers

import com.karasiq.shadowcloud.storage.{BaseRepository, CategorizedRepository}

private[storage] final class PrefixedRepositoryWrapper(repository: BaseRepository, delimiter: String)
  extends RepositoryKeyMapper[String, (String, String)](repository, PrefixedRepositoryWrapper.split(_, delimiter),
    PrefixedRepositoryWrapper.combine(_, delimiter)) with CategorizedRepository[String, String]

private object PrefixedRepositoryWrapper {
  def split(str: String, delimiter: String): (String, String) = {
    val Array(seqKey, itemKey) = str.split(delimiter, 2)
    (seqKey, itemKey)
  }

  def combine(key: (String, String), delimiter: String): String = {
    val (seqKey, itemKey) = key
    s"$seqKey$delimiter$itemKey"
  }
}