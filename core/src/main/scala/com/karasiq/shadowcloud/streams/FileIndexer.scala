package com.karasiq.shadowcloud.streams

import java.io.IOException

import akka.stream._
import akka.stream.stage._
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.index.{Checksum, Chunk}
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.streams.FileIndexer.IndexedFile

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

object FileIndexer {
  case class IndexedFile(checksum: Checksum, chunks: Seq[Chunk])

  def apply(registry: ModuleRegistry, method: HashingMethod = HashingMethod.default): FileIndexer = {
    new FileIndexer(registry, method)
  }
}

// TODO: Content type
final class FileIndexer(registry: ModuleRegistry, method: HashingMethod) extends GraphStageWithMaterializedValue[SinkShape[Chunk], Future[IndexedFile]] {
  val inlet = Inlet[Chunk]("FileIndexer.in")
  val shape = SinkShape(inlet)

  @scala.throws[Exception](classOf[Exception])
  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[IndexedFile]) = {
    val promise = Promise[IndexedFile]
    val logic = new GraphStageLogic(shape) with InHandler {
      private[this] val plainHash = registry.hashingModule(method)
      private[this] val encryptedHash = registry.hashingModule(method)
      private[this] var plainSize = 0L
      private[this] var encryptedSize = 0L
      private[this] val chunks = Vector.newBuilder[Chunk]

      def onPush(): Unit = {
        val chunk = grab(inlet)
        plainHash.update(chunk.data.plain)
        encryptedHash.update(chunk.data.encrypted)
        plainSize += chunk.data.plain.length
        encryptedSize += chunk.data.encrypted.length
        chunks += chunk.withoutData
        pull(inlet)
      }

      override def onUpstreamFinish(): Unit = {
        val indexedFile = IndexedFile(Checksum(method, plainSize, plainHash.createHash(), encryptedSize, encryptedHash.createHash()), chunks.result())
        promise.trySuccess(indexedFile)
        completeStage()
      }

      override def preStart(): Unit = {
        super.preStart()
        tryPull(inlet)
      }

      override def postStop(): Unit = {
        promise.tryFailure(new IOException("Stream terminated"))
        super.postStop()
      }

      setHandler(inlet, this)
    }
    (logic, promise.future)
  }
}
