package com.karasiq.shadowcloud.webdav

import java.net.URLEncoder

import scala.collection.JavaConverters._
import scala.concurrent.Future

import akka.dispatch.MessageDispatcher
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import com.github.sardine.SardineFactory

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

object SardineRepository {
  def apply(props: StorageProps)(implicit dispatcher: MessageDispatcher): SardineRepository = {
    new SardineRepository(props)
  }

  private[webdav] def getResourceURL(baseUrl: String, path: Path): String = {
    val urlWithSlash = if (baseUrl.endsWith("/")) baseUrl else baseUrl + "/"
    val encodedPath = path.nodes.map(URLEncoder.encode(_, "UTF-8"))
    urlWithSlash + encodedPath.mkString("/")
  }

  private[webdav] def createSardine(props: StorageProps) = {
    SardineFactory.begin(props.credentials.login, props.credentials.password)
  }
}

class SardineRepository(props: StorageProps)(implicit dispatcher: MessageDispatcher) extends PathTreeRepository {
  private[this] val baseUrl = props.address.uri.map(_.toString).getOrElse(throw new IllegalArgumentException("No WebDav URL"))

  def keys = subKeys(Path.root)

  def read(key: Path) = {
    Source.single(key)
      .statefulMapConcat { () ⇒
        val sardine = SardineRepository.createSardine(props)
        path ⇒ {
          val resourceUrl = SardineRepository.getResourceURL(baseUrl, path)
          StreamConverters.fromInputStream(() ⇒ sardine.get(resourceUrl)) :: Nil
        }
      }
      .viaMat(AkkaStreamUtils.flatMapConcatMat(identity))(Keep.right)
      .mapMaterializedValue(_.map(rs ⇒ StorageUtils.foldIOResults(rs.map(StorageUtils.wrapAkkaIOResult(key, _)): _*)))
      .alsoToMat(StorageUtils.countPassedBytes(key).toMat(Sink.head)(Keep.right))(Keep.right)
      .withAttributes(ActorAttributes.dispatcher(dispatcher.id))
      .named("webdavRead")
  }

  def write(key: Path) = {
    Flow[Data]
      .via(AkkaStreamUtils.extractUpstream)
      .statefulMapConcat { () ⇒
        val sardine = SardineRepository.createSardine(props)
        stream ⇒ {
          val resourceUrl = SardineRepository.getResourceURL(baseUrl, key)
          (stream, StreamConverters.asInputStream().mapMaterializedValue { inputStream ⇒
            sardine.put(resourceUrl, inputStream)
            Future.successful[StorageIOResult](StorageIOResult.Success(key, sardine.list(resourceUrl, 0).asScala.head.getContentLength))
          }) :: Nil
        }
      }
      .viaMat(AkkaStreamUtils.flatMapConcatMat { case (source, sink) ⇒ source.alsoToMat(sink)(Keep.right) })(Keep.right)
      .mapMaterializedValue(_.map(StorageUtils.foldIOResults))
      .to(Sink.ignore)
      .withAttributes(ActorAttributes.dispatcher(dispatcher.id))
      .named("webdavWrite")
  }

  def delete = {
    Flow[Path]
      .statefulMapConcat { () ⇒
        val sardine = SardineRepository.createSardine(props)
        path ⇒ {
          val resourceUrl = SardineRepository.getResourceURL(baseUrl, path)
          val size = sardine.list(resourceUrl, 0).asScala.head.getContentLength
          sardine.delete(resourceUrl)
          StorageIOResult.Success(path, size) :: Nil
        }
      }
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider) and ActorAttributes.dispatcher(dispatcher.id))
      .named("webdavDelete")
  }

  override def subKeys(fromPath: Path) = {
    Source.single(fromPath)
      .statefulMapConcat { () ⇒
        val sardine = SardineRepository.createSardine(props)
        path ⇒ {
          val resourceUrl = SardineRepository.getResourceURL(baseUrl, path)
          sardine.list(resourceUrl, -1).asScala
            .filter(!_.isDirectory)
            .map(_.getPath: Path)
            .toVector
        }
      }
      .map(_.toRelative(fromPath))
      .alsoToMat(StorageUtils.countPassedElements(fromPath).toMat(Sink.head)(Keep.right))(Keep.right)
      .withAttributes(ActorAttributes.dispatcher(dispatcher.id))
      .named("webdavKeys")
  }
}
