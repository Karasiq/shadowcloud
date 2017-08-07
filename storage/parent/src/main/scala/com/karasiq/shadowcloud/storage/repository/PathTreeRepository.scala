package com.karasiq.shadowcloud.storage.repository

import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage.repository.wrappers.{PathStringRepositoryWrapper, RepositoryKeyMapper}

trait PathTreeRepository extends Repository[Path] {
  def subKeys(fromPath: Path): Source[Path, Result] = {
    keys
      .filter(_.startsWith(fromPath))
      .map(_.toRelative(fromPath))
  }
}

object PathTreeRepository {
  def apply(repository: Repository[Path]): PathTreeRepository = repository match {
    case ptr: PathTreeRepository ⇒
      ptr

    case _ ⇒
      new RepositoryKeyMapper[Path, Path](repository, identity, identity) with PathTreeRepository
  }

  def traverse(repository: PathTreeRepository, path: Path): PathTreeRepository = {
    new RepositoryKeyMapper[Path, Path](repository, _.toRelative(path), path / _) with PathTreeRepository {
      override def subKeys(fromPath: Path): Source[Path, Result] = repository.subKeys(path / fromPath)
      override def keys: Source[Path, Result] = repository.subKeys(path)
    }
  }

  def toCategorized(repository: PathTreeRepository, path: Path = Path.root): CategorizedRepository[String, String] = {
    new RepositoryKeyMapper[Path, (String, String)](repository,
      path ⇒ (path.parent.name, path.name), { case (s1, s2) ⇒ path / s1 / s2 }) with CategorizedRepository[String, String] {

      override def keys: Source[(String, String), Result] = repository.subKeys(path)
        .filter(_.nodes.length == 2) // .filterNot(p ⇒ p.isRoot || p.parent.isRoot)
        .map(path ⇒ (path.parent.name, path.name))

      override def subKeys(seq: String): Source[String, Result] = {
        repository.subKeys(path / seq)
          .filterNot(_.isRoot)
          .map(_.name)
      }
    }
  }

  def toKeyValue(repository: PathTreeRepository, path: Path = Path.root): KeyValueRepository = {
    new RepositoryKeyMapper[Path, String](repository, _.name, path / _) {
      override def keys: Source[String, Result] = repository.subKeys(path)
        .filter(_.parent == path)
        .map(_.name)
    }
  }

  def fromKeyValue(repository: KeyValueRepository, delimiter: String = "_"): PathTreeRepository = {
    new PathStringRepositoryWrapper(repository, delimiter)
  }
}