package com.karasiq.shadowcloud.storage.repository.wrappers


import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.karasiq.shadowcloud.storage.repository.Repository

import scala.util.Try

private[repository] class RepositoryKeyMapper[OldKey, NewKey](repository: Repository[OldKey], toNew: OldKey ⇒ NewKey,
                                                              toOld: NewKey ⇒ OldKey) extends Repository[NewKey] {
  def keys: Source[NewKey, Result] = repository.keys
    .mapConcat(key ⇒ Try(toNew(key)).toOption.toList)
    .named("mappedKeys")
  
  def read(key: NewKey): Source[Data, Result] = repository.read(toOld(key))
  def write(key: NewKey): Sink[Data, Result] = repository.write(toOld(key))
  def delete: Sink[NewKey, Result] = Flow[NewKey].map(toOld).toMat(repository.delete)(Keep.right)

  override def toString: String = {
    s"KeyMapper($repository)"
  }
}
