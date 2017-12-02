package com.karasiq.shadowcloud.storage.repository

import scala.concurrent.{ExecutionContext, Future}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import com.karasiq.common.encoding.{ByteStringEncoding, HexString}
import com.karasiq.shadowcloud.model.{ChunkId, SequenceNr}
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.wrappers.{CategorizedRepositoryKeyMapper, RepositoryKeyMapper, RepositoryWrapper, SubRepositoriesWrapper}

trait Repository[Key] {
  final type Data = ByteString
  final type Result = Future[StorageIOResult]

  def keys: Source[Key, Result]
  def read(key: Key): Source[Data, Result]
  def write(key: Key): Sink[Data, Result]
  def delete: Sink[Key, Result]
}

object Repository {
  def mapKeys[OldKey, NewKey](repository: Repository[OldKey], toNew: OldKey ⇒ NewKey,
                              toOld: NewKey ⇒ OldKey): Repository[NewKey] = {
    new RepositoryKeyMapper(repository, toNew, toOld)
  }

  def mapCategoryKeys[OldKey, NewKey, ItemKey](repository: CategorizedRepository[OldKey, ItemKey], toNew: OldKey ⇒ NewKey,
                                         toOld: NewKey ⇒ OldKey): CategorizedRepository[NewKey, ItemKey] = {
    new CategorizedRepositoryKeyMapper[OldKey, ItemKey, NewKey, ItemKey](repository, toNew, identity, toOld, identity)
  }

  def mapItemKeys[CatKey, OldKey, NewKey](repository: CategorizedRepository[CatKey, OldKey], toNew: OldKey ⇒ NewKey,
                                          toOld: NewKey ⇒ OldKey): CategorizedRepository[CatKey, NewKey] = {
    new CategorizedRepositoryKeyMapper[CatKey, OldKey, CatKey, NewKey](repository, identity, toNew, identity, toOld)
  }

  def forChunks[CatKey](repository: CategorizedRepository[CatKey, String],
                        encoding: ByteStringEncoding = HexString): CategorizedRepository[CatKey, ChunkId] = {
    mapItemKeys(repository, encoding.decode, encoding.encode)
  }

  def forIndex[CatKey](repository: CategorizedRepository[CatKey, String]): CategorizedRepository[CatKey, SequenceNr] = {
    mapItemKeys[CatKey, String, Long](repository, _.toLong, _.toString)
  }

  def toSeq[Key](repository: Repository[Key]): SeqRepository[Key] = repository match {
    case repository: SeqRepository[Key @unchecked] ⇒
      repository

    case _ ⇒
      new RepositoryWrapper(repository) with SeqRepository[Key]
  }

  def toCategorized[CatKey, Key](repository: Repository[(CatKey, Key)]): CategorizedRepository[CatKey, Key] = repository match {
    case repository: CategorizedRepository[CatKey @unchecked, Key @unchecked] ⇒
      repository

    case _ ⇒
      new RepositoryWrapper(repository) with CategorizedRepository[CatKey, Key]
  }

  def fromSubRepositories[CatKey, Key](pathString: String, subRepositories: () ⇒ Source[(CatKey, Repository[Key]), NotUsed])
                                      (implicit ec: ExecutionContext, mat: Materializer): CategorizedRepository[CatKey, Key] = {
    new SubRepositoriesWrapper(pathString, subRepositories)
  }
}
