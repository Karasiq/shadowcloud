package com.karasiq.shadowcloud.storage.repository.wrappers

import com.karasiq.shadowcloud.storage.repository.CategorizedRepository

private[repository] class CategorizedRepositoryKeyMapper[OldCK, OldIK, NewCK, NewIK](
    repository: CategorizedRepository[OldCK, OldIK],
    toNewCK: OldCK ⇒ NewCK,
    toNewIK: OldIK ⇒ NewIK,
    toOldCK: NewCK ⇒ OldCK,
    toOldIK: NewIK ⇒ OldIK
) extends RepositoryKeyMapper[(OldCK, OldIK), (NewCK, NewIK)](
      repository,
      key ⇒ key.copy(toNewCK(key._1), toNewIK(key._2)),
      key ⇒ key.copy(toOldCK(key._1), toOldIK(key._2))
    )
    with CategorizedRepository[NewCK, NewIK] {

  override def subKeys(seq: NewCK) = repository.subKeys(toOldCK(seq)).map(toNewIK)
  override def toString: String    = s"CategorizedKeyMapper($repository)"
}
