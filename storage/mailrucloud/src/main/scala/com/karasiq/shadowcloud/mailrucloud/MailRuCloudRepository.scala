package com.karasiq.shadowcloud.mailrucloud

import scala.language.implicitConversions

import akka.NotUsed
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.mailrucloud.api.{MailCloudClient, MailCloudTypes}
import com.karasiq.mailrucloud.api.MailCloudTypes.{CsrfToken, Nodes, Session}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.ByteStreams

object MailRuCloudRepository {
  def apply(client: MailCloudClient)(implicit nodes: Nodes, session: Session, token: CsrfToken): MailRuCloudRepository = {
    new MailRuCloudRepository(client)
  }

  private implicit def implicitPathToMCPath(path: Path): MailCloudTypes.EntityPath = {
    MailCloudTypes.EntityPath(path.nodes)
  }

  private implicit def implicitMCPathToPath(path: MailCloudTypes.EntityPath): Path = {
    Path(path.path)
  }

  private def countBytes(path: Path) = Flow[ByteString]
    .map(_.length)
    .fold(0L)(_ + _)
    .via(StorageUtils.wrapCountStream(path))
    .toMat(Sink.head)(Keep.right)
    .named("countBytes")
}

class MailRuCloudRepository(client: MailCloudClient)(implicit nodes: Nodes, session: Session, token: CsrfToken) extends PathTreeRepository {
  import MailRuCloudRepository._

  def keys = subKeys(Path.root)

  def read(key: Path) = {
    client.download(key)
      .alsoToMat(countBytes(key))(Keep.right)
      .named("mailrucloudRead")
  }

  def write(key: Path) = {
    Flow[ByteString]
      .via(ByteStreams.concat)
      .flatMapConcat { bytes ⇒
        val future = client.upload(key, HttpEntity.Default(ContentTypes.`application/octet-stream`, bytes.length, Source.single(bytes)))
        Source.fromFuture(future).map(_ ⇒ StorageIOResult.Success(key, bytes.length))
      }
      .via(StorageUtils.foldStream(key))
      .toMat(Sink.head)(Keep.right)
      .named("mailrucloudWrite")
  }

  def delete = {
    Flow[Path]
      .flatMapConcat(path ⇒ Source.fromFuture(client.file(path)).map((path, _)))
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .flatMapMerge(2, { case (path, file) ⇒
        Source.fromFuture(client.delete(path))
          .map(_ ⇒ file.size)
          .via(StorageUtils.wrapCountStream(path))
      })
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .named("mailrucloudDelete")
  }

  override def subKeys(fromPath: Path) = {
    def traverseFlow: Flow[Path, Path, NotUsed] = Flow[Path]
      .mapAsync(1)(client.folder(_))
      .flatMapConcat { folder ⇒
        val (files, folders) = folder.list.partition(_.`type` == "file")
        Source(files.map(_.path: Path).toList)
          .concat(Source(folders.toList).map(_.path: Path).via(traverseFlow))
      }
      .named("mailrucloudTraverse")

    Source.single(fromPath)
      .via(traverseFlow)
      .map(_.toRelative(fromPath))
      .alsoToMat(Flow[Path]
        .fold(0L)((c, _) ⇒ c + 1)
        .via(StorageUtils.wrapCountStream(fromPath))
        .toMat(Sink.head)(Keep.right)
      )(Keep.right)
      .named("mailrucloudKeys")
  }
}
