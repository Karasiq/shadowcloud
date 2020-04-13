package com.karasiq.shadowcloud.actors

import java.io.IOException

import akka.actor.{Actor, ActorLogging, Kill, Props}
import akka.event.Logging
import akka.pattern.pipe
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source, Zip}
import akka.util.ByteString
import akka.{Done, NotUsed}
import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperations}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.CategorizedRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils.Implicits._
import com.karasiq.shadowcloud.streams.utils.{AkkaStreamUtils, ByteStreams}
import com.karasiq.shadowcloud.utils.{ChunkUtils, Utils}

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ChunkIODispatcher {
  @SerialVersionUID(0L)
  final case class ChunkPath(regionId: RegionId, chunkId: ChunkId) {
    require(chunkId.nonEmpty, "Chunk id is empty")

    override def toString: String = {
      val sb = new StringBuilder(regionId.length + 1 + (chunkId.length * 2))
      (sb ++= regionId += '/' ++= HexString.encode(chunkId)).result()
    }

    def toStoragePath: Path = {
      Path.root / regionId / HexString.encode(chunkId)
    }
  }

  // Messages
  sealed trait Message
  final case class WriteChunk(path: ChunkPath, chunk: Chunk) extends Message
  object WriteChunk                                          extends MessageStatus[(ChunkPath, Chunk), Chunk]

  final case class ReadChunk(path: ChunkPath, chunk: Chunk) extends Message
  object ReadChunk                                          extends MessageStatus[(ChunkPath, Chunk), Chunk]

  final case class DeleteChunks(chunks: Set[ChunkPath]) extends Message
  object DeleteChunks                                   extends MessageStatus[Set[ChunkPath], (Set[ChunkPath], StorageIOResult)]

  final case class GetKeys(regionId: RegionId) extends Message
  object GetKeys                               extends MessageStatus[RegionId, Set[ChunkId]]

  // Props
  def props(storageId: StorageId, storageProps: StorageProps, repository: CategorizedRepository[String, ByteString]): Props = {
    Props(new ChunkIODispatcher(storageId, storageProps, repository))
  }
}

private final class ChunkIODispatcher(storageId: StorageId, storageProps: StorageProps, repository: CategorizedRepository[String, ByteString])
    extends Actor
    with ActorLogging {
  import ChunkIODispatcher._
  import context.dispatcher

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val materializer = ActorMaterializer()
  private[this] val sc                    = ShadowCloud()
  private[this] val config                = sc.configs.storageConfig(storageId, storageProps)
  private[this] val chunksWrite           = PendingOperations.withRegionChunk
  private[this] val chunksRead            = PendingOperations.withRegionChunk

  // -----------------------------------------------------------------------
  // Queues
  // -----------------------------------------------------------------------
  private[this] val writeQueue = Source
    .queue[(ChunkPath, Chunk, Promise[StorageIOResult])](config.chunkIO.writeQueueSize, OverflowStrategy.dropNew)
    .flatMapMerge(
      config.chunkIO.writeParallelism, {
        case (path, chunk, promise) ⇒
          val repository = subRepository(path.regionId)
          val writeSource = Source
            .lazySingle(() => chunk.data.encrypted)
            .alsoToMat(repository.write(path.chunkId))(Keep.right) //.alsoToMat(RepositoryStreams.writeWithRetries(repository, path.chunkId))(Keep.right)

          writeSource.extractMatFuture
            .recoverWithRetries(
              1, {
                case StorageException.AlreadyExists(sp) =>
                  log.warning("Chunk already exists: {}", sp)
                  Source
                    .single(path.chunkId)
                    .mapAsync(1)(repository.delete(_))
                    .flatMapConcatMatRight(_ => writeSource)
                    .extractMatFuture
                    .map(_.last)
              }
            )
            .alsoTo(Sink.foreach(result => promise.trySuccess(result)))
            .alsoTo(AkkaStreamUtils.failPromiseOnFailure(promise))
            .viaMat(AkkaStreamUtils.ignoreUpstream(Source.future(promise.future)))(Keep.right)
            .completionTimeout(config.chunkIO.writeTimeout)
            .map(_ => NotUsed)
            .recover { case _ ⇒ NotUsed }
            .named("storageWrite")
      }
    )
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .named("chunkWriteQueue")
    .to(Sink.ignore)
    .run()

  private[this] val readQueue = Source
    .queue[(ChunkPath, Chunk, Promise[(Chunk, StorageIOResult)])](config.chunkIO.readQueueSize, OverflowStrategy.dropNew)
    .flatMapMerge(
      config.chunkIO.readParallelism, {
        case (path, chunk, promise) ⇒
          val repository = subRepository(path.regionId)
          val readSource = repository
            .read(path.chunkId) // RepositoryStreams.readWithRetries(repository, path.chunkId)
            .via(ByteStreams.limit(chunk.checksum.encSize))
            .via(ByteStreams.concat)
            .map(ChunkUtils.chunkWithData(chunk, _))
            // .orElse(Source.failed(new IllegalArgumentException("No data read")))
            .named("storageRead")

          val readGraph = GraphDSL.create(readSource) { implicit builder ⇒ readSource ⇒
            import GraphDSL.Implicits._
            val zipResults = builder.add(Zip[Chunk, StorageIOResult]())

            readSource ~> zipResults.in0
            builder.materializedValue.flatMapConcat(Source.fromFuture) ~> zipResults.in1

            SourceShape(zipResults.out)
          }

          Source
            .fromGraph(readGraph)
            // .log("storage-read-results")
            .alsoToMat(Sink.head)(Keep.right)
            .mapMaterializedValue { f =>
              promise.completeWith(f)
              NotUsed
            }
            .alsoTo(AkkaStreamUtils.failPromiseOnFailure(promise))
            .completionTimeout(config.chunkIO.readTimeout)
            .recover { case _ ⇒ NotUsed }
            .named("storageReadGraph")
      }
    )
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .named("chunkReadQueue")
    .to(Sink.ignore)
    .run()

  // -----------------------------------------------------------------------
  // Actor logic
  // -----------------------------------------------------------------------
  def receive: Receive = {
    case WriteChunk(path, chunk) ⇒
      chunksWrite.addWaiter((path, chunk.withoutData), sender(), () ⇒ writeChunk(path, chunk))

    case ReadChunk(path, chunk) ⇒
      chunksRead.addWaiter((path, chunk.withoutData), sender(), () ⇒ readChunk(path, chunk))

    case msg: WriteChunk.Status ⇒
      chunksWrite.finish(msg.key, msg)

    case msg: ReadChunk.Status ⇒
      chunksRead.finish(msg.key, msg)

    case DeleteChunks(chunks) ⇒
      log.warning("Deleting chunks from storage: [{}]", Utils.printValues(chunks))
      deleteChunks(chunks).pipeTo(sender())

    case GetKeys(regionId) ⇒
      listKeys(regionId).pipeTo(sender())
  }

  // -----------------------------------------------------------------------
  // Lifecycle
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    def stopOnComplete(f: Future[Done]) = {
      f.onComplete(result ⇒ log.error("Queue stopped: {}", result))
      f.map(_ ⇒ Kill).pipeTo(self)
    }

    super.preStart()

    stopOnComplete(writeQueue.watchCompletion())
    stopOnComplete(readQueue.watchCompletion())
  }

  override def postStop(): Unit = {
    chunksWrite.finishAll(
      key => WriteChunk.Failure(key, StorageException.IOFailure(key._1.toStoragePath, new RuntimeException("Chunk IO dispatcher stopped")))
    )
    chunksRead.finishAll(
      key => ReadChunk.Failure(key, StorageException.IOFailure(key._1.toStoragePath, new RuntimeException("Chunk IO dispatcher stopped")))
    )
    writeQueue.complete()
    readQueue.complete()
    super.postStop()
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def subRepository(regionId: RegionId) = {
    repository.subRepository(regionId)
  }

  private[this] def listKeys(regionId: RegionId): Future[GetKeys.Status] = {
    val future = subRepository(regionId).keys
      .runFold(Set.empty[ChunkId])(_ + _)
    GetKeys.wrapFuture(storageId, future)
  }

  private[this] def writeChunk(path: ChunkPath, chunk: Chunk): Unit = {
    val promise     = Promise[StorageIOResult]
    val queueFuture = writeQueue.offer((path, chunk, promise))
    queueFuture.onComplete {
      case Success(QueueOfferResult.Enqueued) ⇒
      // Ignore

      case Success(_) ⇒
        promise.failure(new IOException("Write queue is full"))

      case Failure(exc) ⇒
        promise.failure(exc)
    }

    promise.future.onComplete {
      case Success(StorageIOResult.Success(storagePath, written)) ⇒
        log.debug("{} bytes written to {}, chunk: {} ({})", written, storagePath, chunk, path)
        sc.eventStreams.publishStorageEvent(storageId, StorageEvents.ChunkWritten(path, chunk))
        self ! WriteChunk.Success((path, chunk), chunk)

      case Success(StorageIOResult.Failure(storagePath, error)) ⇒
        log.error(error, "Chunk write error to {}: {} ({})", storagePath, chunk, path)
        self ! WriteChunk.Failure((path, chunk), error)

      case Failure(error) ⇒
        log.error(error, "Chunk write error: {}", chunk)
        self ! WriteChunk.Failure((path, chunk), error)
    }
  }

  private[this] def readChunk(path: ChunkPath, chunk: Chunk): Unit = {
    val promise     = Promise[(Chunk, StorageIOResult)]
    val queueFuture = readQueue.offer((path, chunk, promise))

    queueFuture.onComplete {
      case Success(QueueOfferResult.Enqueued) ⇒ // Ignore
      case Success(_)                         ⇒ promise.failure(new IOException("Read queue is full"))
      case Failure(exc)                       ⇒ promise.failure(exc)
    }

    promise.future.onComplete {
      case Success((chunkWithData, StorageIOResult.Success(storagePath, bytes))) ⇒
        log.debug("{} bytes read from {}, chunk: {}", bytes, storagePath, chunkWithData)
        self ! ReadChunk.Success((path, chunk), chunkWithData)

      case Success((_, StorageIOResult.Failure(storagePath, error))) ⇒
        log.error(error, "Chunk read error from {}: {}", storagePath, chunk)
        self ! ReadChunk.Failure((path, chunk), error)

      case Failure(error) ⇒
        log.error(error, "Chunk read error: {}", chunk)
        self ! ReadChunk.Failure((path, chunk), error)
    }
  }

  private[this] def deleteChunks(paths: Set[ChunkPath]): Future[DeleteChunks.Status] = {
    val pathsByRegion = paths.groupBy(_.regionId).toVector.map {
      case (region, chunks) ⇒
        val localRepository = subRepository(region)
        localRepository → chunks
    }

    val (ioResult, deleted) = Source(pathsByRegion)
      .viaMat(AkkaStreamUtils.flatMapConcatMat {
        case (repository, chunks) ⇒
          val deleteSink = Flow[ChunkPath]
            .map(_.chunkId)
            .toMat(repository.delete)(Keep.right)

          Source(chunks)
            .log("chunks-delete")
            .alsoToMat(deleteSink)(Keep.right)
            .withAttributes(
              Attributes.logLevels(onElement = Logging.WarningLevel) and ActorAttributes.supervisionStrategy(Supervision.resumingDecider)
            )
      })(Keep.right)
      .mapMaterializedValue(_.map(StorageUtils.foldIOResultsIgnoreErrors))
      .idleTimeout(sc.config.timeouts.chunksDelete)
      .toMat(Sink.fold(Set.empty[ChunkPath])(_ + _))(Keep.both)
      .named("deleteChunks")
      .run()

    DeleteChunks.wrapFuture(paths, deleted.zip(ioResult))
  }
}
