package com.karasiq.shadowcloud.storage.repository.wrappers

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.common.encoding.ByteStringEncoding
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository

class PathNodesMapper(repository: PathTreeRepository, toNew: String ⇒ String, toOld: String ⇒ String) extends PathTreeRepository {
  override def keys: Source[Path, Result]           = repository.keys.map(toCleanPath)
  def subKeys(fromPath: Path): Source[Path, Result] = repository.subKeys(toStoragePath(fromPath)).map(toCleanPath)
  def read(key: Path): Source[Data, Result]         = repository.read(toStoragePath(key))
  def write(key: Path): Sink[Data, Result]          = repository.write(toStoragePath(key))
  def delete: Sink[Path, Result]                    = Flow[Path].map(toStoragePath).toMat(repository.delete)(Keep.right)

  private[this] def toStoragePath(path: Path) = Path(path.nodes.map(toNew))
  private[this] def toCleanPath(path: Path)   = Path(path.nodes.map(toOld))
}

object PathNodesMapper {
  def apply(repository: PathTreeRepository, toNew: String ⇒ String, toOld: String ⇒ String): PathTreeRepository = {
    new PathNodesMapper(repository, toNew, toOld)
  }

  def urlEncode(repository: PathTreeRepository): PathTreeRepository = {
    import java.net.{URLDecoder, URLEncoder}
    apply(repository, str ⇒ URLEncoder.encode(str, "UTF-8"), str ⇒ URLDecoder.decode(str, "UTF-8"))
  }

  def encode(repository: PathTreeRepository, encoding: ByteStringEncoding): PathTreeRepository = {
    apply(repository, str ⇒ encoding.encode(ByteString(str)), str ⇒ encoding.decode(str).utf8String)
  }
}
