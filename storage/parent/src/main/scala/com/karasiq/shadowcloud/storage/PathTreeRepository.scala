package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.Source

import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage.wrappers.RepositoryKeyMapper

trait PathTreeRepository extends Repository[Path] {
  def subKeys(fromPath: Path): Source[Path, Result] = {
    keys
      .filter(_.startsWith(fromPath))
      .map(_.toRelative(fromPath))
  }
}

object PathTreeRepository {
  def traverse(repository: PathTreeRepository, path: Path): PathTreeRepository = {
    new RepositoryKeyMapper[Path, Path](repository, _.toRelative(path), path / _) with PathTreeRepository {
      override def subKeys(fromPath: Path): Source[Path, Result] = repository.subKeys(path / fromPath)
      override def keys: Source[Path, Result] = repository.subKeys(path)
    }
  }

  def toCategorized(repository: PathTreeRepository, path: Path = Path.root): CategorizedRepository[String, String] = {
    val mapper = new RepositoryKeyMapper[Path, (String, String)](repository,
      path ⇒ (path.parent.name, path.name), { case (s1, s2) ⇒ path / s1 / s2 }) {

      override def keys: Source[(String, String), Result] = repository.subKeys(path)
        .filter(_.parent.parent == path)
        .filterNot(p ⇒ p.isRoot || p.parent.isRoot)
        .map(path ⇒ (path.parent.name, path.name))
    }
    Repository.toCategorized(mapper)
  }

  def toFlatRepository(repository: PathTreeRepository, path: Path = Path.root): Repository[String] = {
    new RepositoryKeyMapper[Path, String](repository, _.name, path / _) {
      override def keys: Source[String, Result] = repository.subKeys(path)
        .filter(_.parent == path)
        .map(_.name)
    }
  }
}