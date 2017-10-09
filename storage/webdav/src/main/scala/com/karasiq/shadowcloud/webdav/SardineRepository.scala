package com.karasiq.shadowcloud.webdav

import java.net.URLEncoder

import scala.collection.JavaConverters._
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import akka.NotUsed
import akka.dispatch.MessageDispatcher
import akka.stream.{ActorAttributes, Supervision}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import com.github.sardine.{DavResource, Sardine, SardineFactory}

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
  private[this] val rootUrl = props.address.uri.map(_.toString).getOrElse(throw new IllegalArgumentException("No WebDav URL"))
  private[this] val baseUrl = SardineRepository.getResourceURL(rootUrl, props.address.path)
  private[this] val cachedDirectories = TrieMap.empty[Path, DavResource]

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
          val sink = AkkaStreamUtils.writeInputStream { inputStream ⇒
            val result = Future {
              makeDirectories(sardine, key.parent)
              sardine.put(resourceUrl, inputStream)
              val result = sardine.list(resourceUrl, 0).asScala.head
              StorageIOResult.Success(key, result.getContentLength)
            }
            result.onComplete(_.failed.foreach(_ ⇒ inputStream.close()))
            Source.fromFuture(StorageUtils.wrapFuture(key, result))
          }.toMat(Sink.head)(Keep.right)
          (stream, sink) :: Nil
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
      .log("webdav-delete")
      .via(StorageUtils.foldStream())
      .toMat(Sink.head)(Keep.right)
      .withAttributes(ActorAttributes.supervisionStrategy(Supervision.restartingDecider) and ActorAttributes.dispatcher(dispatcher.id))
      .named("webdavDelete")
  }

  override def subKeys(fromPath: Path) = {
    Source.single(fromPath)
      .statefulMapConcat { () ⇒
        val sardine = SardineRepository.createSardine(props)
        def traverseDirectory(path: Path): Source[Path, NotUsed] = {
          val resourceUrl = SardineRepository.getResourceURL(baseUrl, path)
          val resources = if (sardine.exists(resourceUrl)) {
            sardine.list(resourceUrl, 1).asScala.toVector
          } else {
            Vector.empty
          }
          val (folders, files) = resources.partition(_.isDirectory)
          Source(files.map(_.getPath: Path))
            .concat(Source(folders.map(_.getPath: Path).filterNot(_ == path)).flatMapConcat(traverseDirectory))
            .named("webdavTraverse")
        }

        path ⇒ traverseDirectory(path) :: Nil
      }
      .flatMapConcat(identity)
      .map(_.toRelative(fromPath))
      .alsoToMat(StorageUtils.countPassedElements(fromPath).toMat(Sink.head)(Keep.right))(Keep.right)
      .withAttributes(ActorAttributes.dispatcher(dispatcher.id))
      .named("webdavKeys")
  }

  protected def makeDirectories(sardine: Sardine, path: Path): Unit = {
    def getCached(path: Path) = {
      cachedDirectories.getOrElseUpdate(path, sardine.list(SardineRepository.getResourceURL(rootUrl, path), 0).asScala.head)
    }

    Try(getCached(props.address.path / path)) match {
      case Success(directory) ⇒
        require(directory.isDirectory, "Not a directory")

      case Failure(_) ⇒
        def exists(path: Path): Boolean = {
          val resourceUrl = SardineRepository.getResourceURL(rootUrl, path)
          sardine.exists(resourceUrl)
        }

        def createDirectory(path: Path) = {
          val resourceUrl = SardineRepository.getResourceURL(rootUrl, path)
          sardine.createDirectory(resourceUrl)
        }

        def createDirectoryRec(path: Path): Unit = {
          if (exists(path.parent)) {
            createDirectory(path)
          } else {
            createDirectoryRec(path.parent)
            createDirectory(path)
          }
        }

        createDirectoryRec(props.address.path / path)
    }
  }
}
