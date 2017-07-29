package com.karasiq.shadowcloud.storage.repository.wrappers

import com.karasiq.shadowcloud.storage.repository.{CategorizedRepository, KeyValueRepository}

private[repository] final class PrefixedRepositoryWrapper(repository: KeyValueRepository, delimiter: String)
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