package com.karasiq.shadowcloud.actors

import java.io.IOException

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Kill, Props}
import akka.pattern.pipe
import akka.stream._
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Sink, Source, Zip}
import akka.util.ByteString

import com.karasiq.common.encoding.HexString
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperations}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.CategorizedRepository
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.utils.{AkkaStreamUtils, Utils}

object ChunkIODispatcher {
  case class ChunkPath(regionId: RegionId, chunkId: ChunkId) {
    override def toString: String = {
      val sb = new StringBuilder(regionId.length + 1 + (chunkId.length * 2))
      (sb ++= regionId += '/' ++= HexString.encode(chunkId)).result()
    }
  }

  // Messages
  sealed trait Message
  case class WriteChunk(path: ChunkPath, chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[(ChunkPath, Chunk), Chunk]

  case class ReadChunk(path: ChunkPath, chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[(ChunkPath, Chunk), Chunk]

  case class DeleteChunks(chunks: Set[ChunkPath]) extends Message
  object DeleteChunks extends MessageStatus[Set[ChunkPath], (Set[ChunkPath], StorageIOResult)]

  case class GetKeys(regionId: RegionId) extends Message
  object GetKeys extends MessageStatus[RegionId, Set[ChunkId]]

  // Props
  def props(storageId: StorageId, storageProps: StorageProps, repository: CategorizedRepository[String, ByteString]): Props = {
    Props(new ChunkIODispatcher(storageId, storageProps, repository))
  }
}

private final class ChunkIODispatcher(storageId: StorageId, storageProps: StorageProps,
                                      repository: CategorizedRepository[String, ByteString]) extends Actor with ActorLogging {
  import context.dispatcher

  import ChunkIODispatcher._
  implicit val materializer: Materializer = ActorMaterializer()
  private[this] val sc = ShadowCloud()
  private[this] val chunksWrite = PendingOperations.withRegionChunk
  private[this] val chunksRead = PendingOperations.withRegionChunk

  private[this] val writeQueue = Source
    .queue[(ChunkPath, Chunk, Promise[StorageIOResult])](sc.config.queues.storageWrite, OverflowStrategy.dropNew)
    .flatMapConcat { case (path, chunk, promise) ⇒
      val repository = subRepository(path.regionId)
      Source.single(chunk.data.encrypted)
        //.alsoToMat(RepositoryStreams.writeWithRetries(repository, path.chunkId))(Keep.right)
        .alsoToMat(repository.write(path.chunkId))(Keep.right)
        .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
        .map(_ ⇒ NotUsed)
        .mapMaterializedValue { f ⇒ promise.completeWith(f); NotUsed }
        .completionTimeout(sc.config.timeouts.chunkWrite)
    }
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .named("chunkWriteQueue")
    .to(Sink.ignore)
    .run()

  private[this] val readQueue = Source
    .queue[(ChunkPath, Chunk, Promise[(Chunk, StorageIOResult)])](sc.config.queues.storageRead, OverflowStrategy.dropNew)
    .flatMapConcat { case (path, chunk, promise) ⇒
      val localRepository = subRepository(path.regionId)
      val readSource = localRepository.read(path.chunkId)// RepositoryStreams.readWithRetries(localRepository, path.chunkId)
        .completionTimeout(sc.config.timeouts.chunkRead)
        .via(ByteStreams.limit(chunk.checksum.encSize))
        .via(ByteStreams.concat)
        .map(bs ⇒ chunk.copy(data = chunk.data.copy(encrypted = bs)))
        // .recover { case _ ⇒ chunk }

      GraphDSL.create(readSource) { implicit builder ⇒ readSource ⇒
        import GraphDSL.Implicits._

        val zipResults = builder.add(Zip[Chunk, StorageIOResult]())
        val completePromise = builder.add(Flow[(Chunk, StorageIOResult)]
          .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
          .alsoTo(Sink.foreach[(Chunk, StorageIOResult)](promise.trySuccess))
          .map(_ ⇒ NotUsed)
        )

        readSource ~> zipResults.in0
        builder.materializedValue.flatMapConcat(Source.fromFuture) ~> zipResults.in1
        zipResults.out ~> completePromise
        SourceShape(completePromise.out)
      }
    }
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .named("chunkReadQueue")
    .to(Sink.ignore)
    .run()

  def receive: Receive = {
    case WriteChunk(path, chunk) ⇒
      chunksWrite.addWaiter((path, chunk), sender(), () ⇒ writeChunk(path, chunk))

    case ReadChunk(path, chunk) ⇒
      chunksRead.addWaiter((path, chunk), sender(), () ⇒ readChunk(path, chunk))

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

  override def preStart(): Unit = {
    super.preStart()

    writeQueue.watchCompletion()
      .map(_ ⇒ Kill)
      .pipeTo(self)

    readQueue.watchCompletion()
      .map(_ ⇒ Kill)
      .pipeTo(self)
  }

  override def postStop(): Unit = {
    writeQueue.complete()
    readQueue.complete()
    super.postStop()
  }

  private[this] def subRepository(region: String) = {
    repository.subRepository(region)
  }

  private def listKeys(regionId: RegionId): Future[GetKeys.Status] = {
    val future = subRepository(regionId).keys
      .runFold(Set.empty[ChunkId])(_ + _)
    GetKeys.wrapFuture(storageId, future)
  }

  private[this] def writeChunk(path: ChunkPath, chunk: Chunk): Unit = {
    val promise = Promise[StorageIOResult]
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
    val promise = Promise[(Chunk, StorageIOResult)]
    val queueFuture = readQueue.offer((path, chunk, promise))

    queueFuture.onComplete {
      case Success(QueueOfferResult.Enqueued) ⇒
        // Ignore

      case Success(_) ⇒
        promise.failure(new IOException("Read queue is full"))

      case Failure(exc) ⇒
        promise.failure(exc)
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
    val pathsByRegion = paths.groupBy(_.regionId).toVector.map { case (region, chunks) ⇒
      val localRepository = subRepository(region)
      localRepository → chunks
    }

    val (ioResult, deleted) = Source(pathsByRegion)
      .viaMat(AkkaStreamUtils.flatMapConcatMat { case (repository, chunks) ⇒
        val deleteSink = Flow[ChunkPath]
          .map(_.chunkId)
          .toMat(repository.delete)(Keep.right)

        Source(chunks)
          .alsoToMat(deleteSink)(Keep.right)
          .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      })(Keep.right)
      .mapMaterializedValue(_.map(StorageUtils.foldIOResultsIgnoreErrors))
      .toMat(Sink.fold(Set.empty[ChunkPath])(_ + _))(Keep.both)
      .named("deleteChunks")
      .run()

    DeleteChunks.wrapFuture(paths, deleted.zip(ioResult))
  }
}
