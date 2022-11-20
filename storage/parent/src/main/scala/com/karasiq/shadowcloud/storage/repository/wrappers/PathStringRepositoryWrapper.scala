package com.karasiq.shadowcloud.storage.repository.wrappers

import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.repository.{KeyValueRepository, PathTreeRepository}

private[repository] final class PathStringRepositoryWrapper(repository: KeyValueRepository, delimiter: String)
    extends RepositoryKeyMapper[String, Path](
      repository,
      PathStringRepositoryWrapper.split(_, delimiter),
      PathStringRepositoryWrapper.combine(_, delimiter)
    )
    with PathTreeRepository {

  def subKeys(fromPath: Path): Source[Path, Result] = {
    repository.keys
      .map(PathStringRepositoryWrapper.split(_, delimiter))
      .via(PathTreeRepository.extractSubPaths(fromPath))
  }

  override def toString: String = {
    s"PathString($repository, $delimiter)"
  }
}

private object PathStringRepositoryWrapper {
  def split(str: String, delimiter: String): Path = {
    val nodes = str.split(delimiter)
    Path(nodes.toVector)
  }

  def combine(key: Path, delimiter: String): String = {
    require(key.nodes.forall(n ⇒ !n.contains(delimiter)), "Cannot map path to string: " + key)
    key.nodes.mkString(delimiter)
  }
}
