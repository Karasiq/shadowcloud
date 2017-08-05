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

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperations}
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.CategorizedRepository
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.utils.HexString

object ChunkIODispatcher {
  case class ChunkPath(region: String, id: ByteString) {
    override def toString: String = {
      val sb = new StringBuilder(region.length + 1 + (id.length * 2))
      (sb ++= region += '/' ++= HexString.encode(id)).result()
    }
  }

  // Messages
  sealed trait Message
  case class WriteChunk(path: ChunkPath, chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[(ChunkPath, Chunk), Chunk]

  case class ReadChunk(path: ChunkPath, chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[(ChunkPath, Chunk), Chunk]

  case class DeleteChunks(chunks: Set[ChunkPath]) extends Message
  object DeleteChunks extends MessageStatus[Set[ChunkPath], Set[ChunkPath]]

  case object GetKeys extends Message with MessageStatus[NotUsed, Set[ChunkPath]]

  // Props
  def props(storageId: String, storageProps: StorageProps, repository: CategorizedRepository[String, ByteString]): Props = {
    Props(new ChunkIODispatcher(storageId, storageProps, repository))
  }
}

private final class ChunkIODispatcher(storageId: String, storageProps: StorageProps,
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
      val repository = subRepository(path.region)
      Source.single(chunk.data.encrypted)
        .alsoToMat(repository.write(path.id))(Keep.right)
        .completionTimeout(sc.config.timeouts.chunkWrite)
        .map(_ ⇒ NotUsed)
        .mapMaterializedValue { result ⇒
          promise.completeWith(result)
          NotUsed
        }
    }
    .addAttributes(Attributes.name("chunkWriteQueue") and ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .to(Sink.ignore)
    .run()

  private[this] val readQueue = Source
    .queue[(ChunkPath, Chunk, Promise[(Chunk, StorageIOResult)])](sc.config.queues.storageRead, OverflowStrategy.dropNew)
    .flatMapConcat { case (path, chunk, promise) ⇒
      val localRepository = subRepository(path.region)
      val readSource = localRepository.read(path.id)
        .completionTimeout(sc.config.timeouts.chunkRead)
        .via(ByteStreams.limit(chunk.checksum.encSize))
        .via(ByteStreams.concat)
        .map(bs ⇒ chunk.copy(data = chunk.data.copy(encrypted = bs)))
        .recover { case _ ⇒ chunk }

      GraphDSL.create(readSource) { implicit builder ⇒ readSource ⇒
        import GraphDSL.Implicits._

        val zipResults = builder.add(Zip[Chunk, StorageIOResult]())
        val completePromise = builder.add(Flow[(Chunk, StorageIOResult)]
          .alsoTo(Sink.onComplete {
            case Failure(exc) ⇒
              promise.tryFailure(exc)

            case _ ⇒
              // Ignore
          })
          .alsoTo(Sink.foreach[(Chunk, StorageIOResult)](promise.trySuccess))
          .map(_ ⇒ NotUsed)
        )

        readSource ~> zipResults.in0
        builder.materializedValue.flatMapConcat(Source.fromFuture) ~> zipResults.in1
        zipResults.out ~> completePromise
        SourceShape(completePromise.out)
      }
    }
    .addAttributes(Attributes.name("chunkReadQueue") and ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
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
      deleteChunks(chunks).pipeTo(sender())

    case GetKeys ⇒
      listKeys().pipeTo(sender())
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

  private def listKeys(): Future[GetKeys.Status] = {
    val future = repository.keys.map(ChunkPath.tupled).runFold(Set.empty[ChunkPath])(_ + _)
    GetKeys.wrapFuture(NotUsed, future)
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
    val byRegion = paths.groupBy(_.region).toVector.flatMap { case (region, chunks) ⇒
      val localRepository = subRepository(region)
      chunks.map(localRepository → _)
    }

    val deleted = Source(byRegion)
      .mapAsync(sc.config.parallelism.write) { case (lr, path) ⇒
        lr.delete(path.id)
          .filter(_.isSuccess)
          .map(_ ⇒ path)
      }
      .addAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
      .fold(Set.empty[ChunkPath])(_ + _)
      .toMat(Sink.head)(Keep.right)
      .named("deleteChunks")
      .run()

    DeleteChunks.wrapFuture(paths, deleted)
  }
}
