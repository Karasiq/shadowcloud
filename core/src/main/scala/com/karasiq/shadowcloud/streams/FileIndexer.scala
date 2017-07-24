package com.karasiq.shadowcloud.streams

import java.io.IOException

import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import akka.Done
import akka.stream._
import akka.stream.scaladsl.Sink
import akka.stream.stage._
import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.HashingMethod
import com.karasiq.shadowcloud.index.{Checksum, Chunk}
import com.karasiq.shadowcloud.providers.CryptoModuleRegistry
import com.karasiq.shadowcloud.streams.FileIndexer.Result
import com.karasiq.shadowcloud.utils.ChunkUtils

private[shadowcloud] object FileIndexer {
  case class Result(checksum: Checksum, chunks: Seq[Chunk], ioResult: IOResult)

  def apply(registry: CryptoModuleRegistry, plainHashing: HashingMethod = HashingMethod.default,
            encryptedHashing: HashingMethod = HashingMethod.default): Sink[Chunk, Future[Result]] = {
    Sink.fromGraph(new FileIndexer(registry, plainHashing, encryptedHashing))
  }
}

private[shadowcloud] final class FileIndexer(cryptoModules: CryptoModuleRegistry,
                                             plainHashing: HashingMethod,
                                             encryptedHashing: HashingMethod)
  extends GraphStageWithMaterializedValue[SinkShape[Chunk], Future[Result]] {

  val inlet = Inlet[Chunk]("FileIndexer.in")
  val shape = SinkShape(inlet)

  @scala.throws[Exception](classOf[Exception])
  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Result]) = {
    val promise = Promise[Result]
    val logic = new GraphStageLogic(shape) with InHandler {
      private[this] val hasher = Some(plainHashing)
        .filterNot(_.algorithm.isEmpty)
        .map(CryptoModuleRegistry.streamHashingModule(cryptoModules, _))

      private[this] val encHasher = Some(encryptedHashing)
        .filterNot(_.algorithm.isEmpty)
        .map(CryptoModuleRegistry.streamHashingModule(cryptoModules, _))

      private[this] var plainSize = 0L
      private[this] var encryptedSize = 0L
      private[this] val chunks = Vector.newBuilder[Chunk]

      private[this] def update(chunk: Chunk): Unit = {
        hasher.foreach { module ⇒
          val data = ChunkUtils.getPlainBytes(cryptoModules, chunk)
          module.update(data)
        }

        encHasher.foreach { module ⇒
          val data = ChunkUtils.getEncryptedBytes(cryptoModules, chunk)
          module.update(data)
        }

        plainSize += chunk.checksum.size
        encryptedSize += chunk.checksum.encSize
        chunks += chunk.withoutData
      }

      private[this] def finish(status: Try[Done]): Unit = {
        val checksum = Checksum(plainHashing, encryptedHashing,
          plainSize, hasher.fold(ByteString.empty)(_.createHash()),
          encryptedSize, encHasher.fold(ByteString.empty)(_.createHash()))

        val indexedFile = Result(checksum, chunks.result(), IOResult(plainSize, status))
        promise.trySuccess(indexedFile)
      }

      def onPush(): Unit = {
        val chunk = grab(inlet)
        pull(inlet)
        update(chunk)
      }

      override def onUpstreamFinish(): Unit = {
        finish(Success(Done))
        completeStage()
      }


      override def onUpstreamFailure(ex: Throwable): Unit = {
        finish(Failure(ex))
        super.onUpstreamFailure(ex)
      }

      override def preStart(): Unit = {
        super.preStart()
        tryPull(inlet)
      }

      override def postStop(): Unit = {
        finish(Failure(new IOException("Stream terminated")))
        super.postStop()
      }

      setHandler(inlet, this)
    }
    (logic, promise.future)
  }
}
