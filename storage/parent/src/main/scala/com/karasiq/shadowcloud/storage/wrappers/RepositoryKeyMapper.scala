package com.karasiq.shadowcloud.storage.wrappers

import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.storage.Repository

import scala.language.postfixOps

private[storage] class RepositoryKeyMapper[OldKey, NewKey](repository: Repository[OldKey], toNew: OldKey ⇒ NewKey,
                                                           toOld: NewKey ⇒ OldKey) extends Repository[NewKey] {
  def keys: Source[NewKey, Result] = repository.keys.map(toNew)
  def read(key: NewKey): Source[Data, Result] = repository.read(toOld(key))
  def write(key: NewKey): Sink[Data, Result] = repository.write(toOld(key))
  def delete(key: NewKey): Result = repository.delete(toOld(key))

  override def toString: String = {
    s"KeyMapper($repository)"
  }
}
