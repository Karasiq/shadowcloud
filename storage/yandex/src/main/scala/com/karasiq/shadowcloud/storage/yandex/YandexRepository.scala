package com.karasiq.shadowcloud.storage.yandex

import akka.Done
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.storage.yandex.YandexWebApi.YandexSession
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils
import com.karasiq.shadowcloud.utils.CacheMap

import scala.concurrent.ExecutionContext

class YandexRepository(api: YandexWebApi)(implicit session: YandexSession, ec: ExecutionContext) extends PathTreeRepository {
  private[this] val directoryCache = CacheMap[Path, Done]

  override def subKeys(fromPath: Path): Source[Path, Result] =
    Source
      .lazySource(() ⇒ api.list(fromPath))
      .flatMapConcat {
        case ("dir", path) ⇒
          subKeys(path).map(path / _)

        case (_, path) ⇒
          Source.single(path)
      }
      .alsoToMat(StorageUtils.countPassedElements(fromPath).toMat(Sink.head)(Keep.right))(Keep.right)

  override def read(path: Path): Source[Data, Result] = {
    api
      .download(path)
      .alsoToMat(StorageUtils.countPassedBytes(path).toMat(Sink.head)(Keep.right))(Keep.right)
  }

  override def write(path: Path): Sink[Data, Result] = {
    Flow[Data]
      .via(AkkaStreamUtils.extractUpstream)
      .flatMapConcat { upstream ⇒
        Source
          .future(directoryCache(path)(api.createFolder(path.parent)))
          .flatMapConcat(_ ⇒ upstream)
      }
      .alsoToMat(api.upload(path))(Keep.right)
      .mapMaterializedValue(_.map(_ ⇒ StorageIOResult.Success(path, 0)))
      .toMat(StorageUtils.countPassedBytes(path).toMat(Sink.head)(Keep.right))(StorageUtils.foldIOFutures(_, _))
  }

  override def delete: Sink[Path, Result] = {
    Flow[Path]
      .mapAsync(1)(api.delete(_))
      .toMat(StorageUtils.countPassedElements(Path.root).toMat(Sink.head)(Keep.right))(Keep.right)
  }
}
