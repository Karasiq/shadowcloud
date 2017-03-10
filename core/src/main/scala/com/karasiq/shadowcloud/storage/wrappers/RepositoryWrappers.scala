package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.Repository
import com.karasiq.shadowcloud.storage.Repository.BaseRepository

import scala.language.postfixOps

private[shadowcloud] object RepositoryWrappers {
  def hexString(underlying: BaseRepository): Repository[ByteString] = {
    new HexStringRepositoryWrapper(underlying)
  }

  def prefixed(underlying: BaseRepository, delimiter: String = "_"): CategorizedRepository[String, String] = {
    new MultiSeqRepositoryWrapper(underlying, identity, identity, delimiter)
  }

  def longSeq(underlying: BaseRepository): SeqRepository[Long] = {
    new LongSeqRepositoryWrapper(underlying)
  }

  def multiLongSeq(underlying: BaseRepository, delimiter: String = "_"): CategorizedRepository[String, Long] with SeqRepository[(String, Long)] = {
    new MultiSeqRepositoryWrapper(underlying, identity, _.toLong, delimiter)
  }

  def mapKeys[OldKey, NewKey](repository: Repository[OldKey], toNew: OldKey ⇒ NewKey,
                              toOld: NewKey ⇒ OldKey): Repository[NewKey] = {
    new Repository[NewKey] {
      override type Data = repository.Data
      override type Result = repository.Result
      def keys: Source[NewKey, Result] = repository.keys.map(toNew)
      def read(key: NewKey): Source[Data, Result] = repository.read(toOld(key))
      def write(key: NewKey): Sink[Data, Result] = repository.write(toOld(key))
    }
  }

  def mapSubKeys[CatKey, OldKey, NewKey](repository: CategorizedRepository[CatKey, OldKey], toNew: OldKey ⇒ NewKey,
                                         toOld: NewKey ⇒ OldKey): CategorizedRepository[CatKey, NewKey] = {
    new CategorizedRepository[CatKey, NewKey] {
      override type Data = repository.Data
      override type Result = repository.Result
      def keys: Source[(CatKey, NewKey), Result] = repository.keys.map(key ⇒ key.copy(_2 = toNew(key._2)))
      def read(key: (CatKey, NewKey)): Source[Data, Result] = repository.read(key.copy(_2 = toOld(key._2)))
      def write(key: (CatKey, NewKey)): Sink[Data, Result] = repository.write(key.copy(_2 = toOld(key._2)))
    }
  }

  def asIndexRepo[CatKey](repository: CategorizedRepository[CatKey, String]): CategorizedRepository[CatKey, Long] = {
    mapSubKeys[CatKey, String, Long](repository, _.toLong, _.toString)
  }

  def asSeq[Key](repository: Repository[Key]): SeqRepository[Key] = new SeqRepository[Key] {
    override type Data = repository.Data
    override type Result = repository.Result
    def keys: Source[Key, Result] = repository.keys
    def read(key: Key): Source[Data, Result] = repository.read(key)
    def write(key: Key): Sink[Data, Result] = repository.write(key)
  }

  def asCategorized[CatKey, Key](repository: Repository[(CatKey, Key)]): CategorizedRepository[CatKey, Key] = new CategorizedRepository[CatKey, Key] {
    override type Data = repository.Data
    override type Result = repository.Result
    def keys: Source[(CatKey, Key), Result] = repository.keys
    def read(key: (CatKey, Key)): Source[Data, Result] = repository.read(key)
    def write(key: (CatKey, Key)): Sink[Data, Result] = repository.write(key)
  }
}
